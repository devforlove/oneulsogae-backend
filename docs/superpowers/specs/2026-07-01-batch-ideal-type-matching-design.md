# 일일 배치 매칭에 이상형 우선순위 적용 설계

## 배경 / 목표

일일 솔로 매칭 배치(`SoloMatchBatchService`)는 현재 각 대상 유저에 대해 지역 근접(하버사인)순으로 후보를 뒤져 재소개 이력이 없는 첫 후보와 `PROPOSED` 매칭을 만든다. 최근 추가된 이상형(`user_ideal_types`)을 이 배치에 반영한다.

**핵심 원칙: 이상형은 필터가 아니라 우선순위다.** 이상형이 맞지 않는다고 매칭을 막지 않는다. 다른 후보가 없으면 이상형이 전혀 안 맞아도 여전히 매칭한다. 이상형은 후보 정렬 순위에만 개입한다.

**확정된 설계 결정**
- 정렬: **가중 종합 점수** — 이상형 부합 40% / 거리 근접 40% / 최근 로그인 20%.
- 이상형 부합: **양방향(상호)** — 대상→후보, 후보→대상 두 방향 평균.
- 다양성: **상위 동점군 내 무작위** — 점수 버킷 셔플.

## 아키텍처 / 데이터 조달

배치 풀 read model `MatchableUser`(`userId·gender·regionId·lastLoginAt`)는 스코어링에 필요한 속성이 없다. 양방향 이상형 점수를 매기려면 각 유저의 **실제 속성**(나이·키·결혼·흡연·음주·종교)과 **이상형 선호**가 모두 필요하다.

- scheduler 도메인에 신규 조회 포트/DAO `GetMatchScoringProfileDao`를 추가한다.
- infra 읽기 어댑터가 `user_details` + `user_ideal_types`를 조인해 scheduler 전용 read model `MatchScoringProfile`로 투영한다. (아키텍처 규칙: infra 읽기 어댑터가 `UserDetailEntity`를 조인해 자기 도메인 read model로 투영)
- 배치 시작 시 매칭 대상 userId 집합(2주 내 활성)에 대해 **한 번만** 로드해 `Map<Long, MatchScoringProfile>` 인메모리 맵으로 보관한다.
- `match_user` 읽기 모델은 그대로 둔다(의도적 디커플링 유지). 스코어링 데이터만 별도 읽기 경로로 조달 → 이벤트 동기화 불필요.

**`MatchScoringProfile` 필드**

| 구분 | 필드 | 출처 |
|------|------|------|
| 속성 | `userId`, `age: Int?`, `height: Int?`, `maritalStatus?`, `smokingStatus?`, `drinkingStatus?`, `religion?`, `regionId` | `user_details` (나이는 `birthday`→`ageAt(today)`) |
| 이상형 | `idealAgeMin/Max`, `idealHeightMin/Max`, `idealMaritalStatus?`, `idealSmokingStatus?`, `idealDrinkingStatus?`, `idealReligion?` | `user_ideal_types` |

**결측 처리**
- 이상형 행 없음 → 모든 이상형 선호 null = "선호 없음"(중립).
- 속성 없음(예: 키 미입력) → 상대 이상형이 그 조건을 지정했다면 미충족(0)으로 계산. 필터가 아니라 점수만 낮아진다.
- 이상형의 `distance`(SAME_REGION/ADJACENT_REGION) 필드는 스코어링 프로필에 포함하지 않는다(아래 참조).

## 종합 점수 공식

대상 `target`, 후보 `cand`에 대해 세 요소를 0~1로 정규화해 가중 합산한다.

### ① 이상형 부합도 (양방향, 0~1)

한 방향 점수(`A`의 이상형으로 `B`를 평가):

```
지정 조건 = A의 이상형 중 null이 아닌 조건 (6개 중)
충족 조건 = 지정 조건 중 B의 속성이 만족하는 조건 수
directionScore = 지정 조건이 0개면 1.0, 아니면 충족 조건 / 지정 조건
```

- 6개 조건: 나이범위·키범위·결혼·흡연·음주·종교.
  - 나이/키: 범위 `[min, max]` 포함이면 충족(경계 포함). 상대 속성이 null이면 미충족.
  - 결혼/흡연/음주/종교: enum 동등이면 충족. 상대 속성이 null이면 미충족.
- **이상형의 `distance` 조건은 부합도에서 제외** — 지리적 근접은 ②가 이미 40%로 반영하므로 중복 계산을 피한다. (추후 ②에 유저의 SAME/ADJACENT 허용치를 접는 방식으로 개선 검토 가능)

```
idealFit = (target→cand directionScore + cand→target directionScore) / 2
```

### ② 거리 근접 (0~1)

`RegionProximityPort.nearbyRegionIds(target.regionId)`의 순위를 재사용한다(하버사인 재계산·km 노출 없음).

```
rank = nearbyRegionIds에서 cand.regionId의 index (같은 지역=0)
distanceScore = 전체 지역 수가 1 이하이면 1.0, 아니면 1 - rank / (전체 지역 수 - 1)
```

같은 지역=1.0, 가장 먼 지역≈0.

### ③ 최근 로그인 (0~1)

2주 창(`loginAfter = now - 2주`) 안에서 정규화.

```
recencyScore = (cand.lastLoginAt - loginAfter) / (now - loginAfter)   // [0, 1]로 클램프
```

### 종합

```
score = 0.4 * idealFit + 0.4 * distanceScore + 0.2 * recencyScore
```

- 성별: 기존대로 반대 성별 하드 제약(점수 대상 아님).
- 지역: 필터가 아니라 ②로만 반영 — 이상형·지역 불일치가 매칭을 막지 않고 순위만 낮춘다.

## 알고리즘 통합

```
now = timeGenerator.now(); loginAfter = now - 2주; today = now.toLocalDate()
regionProximityPort.refresh()
excluded = 성사(MATCHED·ACTIVE) 유저 ∪ 오늘 소개된 유저      // 기존 유지
matchables = findMatchableUsers(loginAfter) - excluded         // 기존 유지
profiles = getMatchScoringProfileDao.load(matchables의 userId 집합)  // 신규, 1회 로드
pool = MatchPool.of(matchables)                                // 기존

for target in matchables (최근 로그인순, 기존 유지):
  if target not in pool → continue
  candidates = pool.availableCandidates(target.gender.opposite())   // 신규: 가용 반대성별 전체
  scored = candidates.map { c -> c to score(target, c, profiles, nearbyRank, now, loginAfter) }
  ordered = bucketShuffle(scored)   // 점수 버킷(0.05 단위) 내림차순, 같은 버킷 셔플
  partner = ordered.firstOrNull { c -> !getMatchRecordDao.existsByPair(target.userId, c.userId) }
  if partner == null → skipped++; continue
  saveMatchRecordPort.saveProposedMatch(target.userId, target.gender, partner.userId, now)   // 기존
  pool.remove(target); pool.remove(partner); recommended++

noIntroductionAlarmPort.notifySoloUnmatched(pool.remainingUserIds(), now)   // 기존 유지
```

**다양성(상위 동점군 내 무작위)**: `bucketShuffle`은 점수를 0.05 단위 버킷으로 반올림해 묶고, 버킷 순서는 높은 점수→낮은 점수로 유지하되 같은 버킷 안은 주입된 `Random`으로 셔플한다. 최상위뿐 아니라 전 구간의 동점군을 흔들어 매일 같은 상대만 나오는 것을 완화한다. `existsByPair`로 이미 소개된 쌍은 재등장하지 않는다.

**성능**: 대상별로 가용 반대성별 후보 전체를 인메모리 스코어링(비교 몇 번 수준). `existsByPair`(DB)는 기존처럼 점수 상위부터 첫 통과 후보까지만 지연 호출 → DB 호출량은 기존과 유사. 풀은 2주 활성 유저로 한정.

## 변경 대상 요약

| 모듈 | 파일 | 변경 |
|------|------|------|
| scheduler | `MatchScoringProfile`(신규 read model) | 속성 + 이상형 |
| scheduler | `GetMatchScoringProfileDao`(신규 port) | userId 집합 → 프로필 맵 |
| scheduler | `MatchScorer` / 스코어링 도메인(신규) | 순수 점수 계산 + 버킷 셔플 |
| scheduler | `MatchPool` | `availableCandidates(gender)` 노출 추가 |
| scheduler | `SoloMatchBatchService` | 스코어링 기반 후보 선택으로 교체 |
| infra | `GetMatchScoringProfileDaoImpl`(신규) | `user_details`+`user_ideal_types` 조인 투영 |
| common/scheduler | 가중치·버킷 상수 | 40/40/20, 0.05 |

- `RegionShuffler`(지역 셔플)는 새 경로에서 미사용 → 데드코드 여부는 보고만 하고 삭제는 확인 후.
- 대상 순회 순서·재소개 방지·오늘 중복 소개 제외·성사 유저 제외·미소개 알람은 전부 기존 그대로 유지.

## 테스트 전략

**스코어링 유닛 테스트 (Kotest, scheduler)** — 순수 도메인으로 분리해 프레임워크 없이 검증
- 이상형 부합도: 전부 충족→1.0 / 일부→비율 / 대상 이상형 전부 null→1.0 / 후보 속성 결측→미충족.
- 양방향 평균: 한 방향만 높을 때 `(d1+d2)/2`.
- 범위 경계: 나이·키 min/max 경계 포함.
- 종합 점수: 40/40/20 합산값, 거리(같은 지역=1.0, 최원거리=0)·최근(창 끝=0, now=1) 정규화 경계.
- 버킷 셔플: 높은 버킷이 항상 낮은 버킷보다 앞, 같은 버킷 내 무작위(고정 시드 `Random`로 결정론 검증).

**배치 서비스 테스트 (scheduler)** — `TimeGenerator`·시드 `Random` 고정
- 이상형이 잘 맞는 후보가 거리·최근이 비슷할 때 우선 채택.
- **필터가 아님 검증**: 이상형이 전혀 안 맞아도 다른 후보가 없으면 매칭됨(핵심 요구사항).
- 재소개 이력 있는 최고점 후보는 건너뛰고 다음 후보 채택.

**E2E (meeple-api)** — 기존 배치 E2E가 있으면 이상형 데이터 세팅 시나리오 케이스 추가. 없으면 스킵하고 보고.
