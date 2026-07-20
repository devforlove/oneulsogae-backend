# 추가 소개(Extra Intro) 기능 설계

## 배경 / 목표

일일 매칭 배치(`SoloMatchBatchService`)가 매일 정오 한 명을 소개한다. 사용자가 **오늘의 추천 외에 한 명을 더 소개받는** "추가 소개" 기능을 추가한다. 프론트(`/intro/more`)는 소개 가능한 후보 갤러리(선택 불가 쇼케이스)를 보여준 뒤, 코인을 차감하고 시스템이 한 명을 골라 소개한다.

두 API를 solomatch 도메인에 추가한다.
1. **조회**: 소개 가능한 자격 후보 목록(상위 N명 + 전체 후보 수)
2. **추가 소개**: 코인 차감 후 알고리즘으로 한 명을 골라 매칭 생성

**확정된 설계 결정**
- 선택 방식: **이상형 종합점수 상위군 무작위**(배치와 동일). 이를 위해 매칭 알고리즘을 **별도 모듈로 분리**해 배치·추가소개가 공유한다.
- 공용화 범위: 점수 계산기(`MatchScorer`) + **선택 로직(`MatchSelector`)** 까지 공용화.
- 코인: 새 타입 `CoinUsageType.EXTRA_INTRO`(30코인). 소개 성공 시 차감.
- 후속 흐름: `PROPOSED` 매칭 생성 → 기존 관심 보내기(interest) 흐름. 구분용 `SoloMatchType.EXTRA` 신설.
- 조회: 자격 후보를 종합점수로 정렬해 **상위 11명**을 프로필 포함 반환 + **전체 자격 후보 수**를 함께 반환.

## 1. 매칭 알고리즘 공용 모듈 분리 — `oneulsogae-matching`

새 gradle 모듈 신설. 순수 로직(프레임워크·인프라 비의존), `oneulsogae-common`에만 의존. `scheduler`·`core`가 함께 의존한다.

```
oneulsogae-matching ──> oneulsogae-common
oneulsogae-scheduler ──> common, oneulsogae-matching
oneulsogae-core ──> common, oneulsogae-matching
```
matching→common 단방향이라 사이클 없음.

**이동/변경**
- `MatchScorer` (scheduler → `com.org.oneulsogae.matching`): 이동. `orderByScore`를 **제네릭 `<T>`** 로 바꿔 `MatchableUser` 결합 제거(`fun <T> orderByScore(scored: List<Pair<T, Double>>, random: Random): List<T>`).
- `MatchScoringProfile` (scheduler query dto → `com.org.oneulsogae.matching`): 이동. 순수 스코어링 입력이라 공용 모듈이 소유. scheduler/core의 조회 어댑터가 이 타입으로 투영한다.
- **신설 `MatchSelector`** (순수 object): "후보 점수화 → 버킷 무작위 정렬 → 재소개 제외 필터 → 최고점 1명 선택"을 공용화. 배치와 실시간 추가소개가 동일 선택 로직을 쓴다.

**`MatchSelector` 시그니처(안)** — 공용을 위해 후보 최소 계약을 인터페이스로 둔다.
```kotlin
interface ScoringCandidate {
    val userId: Long
    val regionId: Long
    val lastLoginAt: LocalDateTime
}

object MatchSelector {
    /**
     * 후보들을 종합점수(이상형·거리·최근)로 정렬(동점군 무작위)한 뒤,
     * isExcluded가 false인 최고점 후보 1명을 반환한다. 없으면 null.
     */
    fun <T : ScoringCandidate> selectBest(
        targetProfile: MatchScoringProfile?,
        candidates: List<T>,
        profileOf: (T) -> MatchScoringProfile?,
        regionRankByRegionId: Map<Long, Int>,
        regionCount: Int,
        now: LocalDateTime,
        loginAfter: LocalDateTime,
        random: Random,
        isExcluded: (T) -> Boolean,
    ): T?

    /** 정렬만 필요할 때(조회 상위 N) 재소개 필터 없이 점수순 리스트 반환. */
    fun <T : ScoringCandidate> orderByScore(
        targetProfile: MatchScoringProfile?,
        candidates: List<T>,
        profileOf: (T) -> MatchScoringProfile?,
        regionRankByRegionId: Map<Long, Int>,
        regionCount: Int,
        now: LocalDateTime,
        loginAfter: LocalDateTime,
        random: Random,
    ): List<T>
}
```

**scheduler 적응**
- `MatchableUser`가 `ScoringCandidate`를 구현하도록 한다(필드 이미 보유: userId·regionId·lastLoginAt).
- `SoloMatchBatchService.findBestFreshPartner`를 `MatchSelector.selectBest`로 교체(동작 불변). 재소개 제외는 `isExcluded = { getMatchRecordDao.existsByPair(target.userId, it.userId) }`.
- scheduler의 `MatchScorer`/`MatchScoringProfile` import 경로를 새 모듈로 교체. 기존 배치 동작·테스트 결과 불변.

## 2. 조회 API — `GET /matches/v1/extra/candidates`

solomatch **query** 패키지(읽기 전용, `@Transactional(readOnly = true)`).

- in-port `GetExtraIntroCandidatesUseCase.getCandidates(userId: Long): ExtraIntroCandidates`
- `GetExtraIntroCandidatesService`
- 응답 read model
  - `ExtraIntroCandidates(totalCount: Int, candidates: List<ExtraIntroCandidate>)`
  - `ExtraIntroCandidate`: 표시용 프로필(닉네임·프로필이미지·나이/생일·키·성별·직업·회사·대학·활동지역·소개·태그·관심사·결혼·흡연·음주·종교·체형)

**자격 후보 기준** (배치/온보딩과 정합)
- 반대 성별
- 최근 2주 내 로그인
- 요청자 자신 제외
- 재소개 이력 없음: `solo_matches.member_key`에 (요청자, 후보) 조합이 없음(소프트 삭제 포함, 배치와 동일 기준)
- 매칭 가능(match_user 읽기 모델 존재)

**두 단계 조회(DAO)** — 전체 수는 세고, 프로필은 11명만 적재한다.
- `GetExtraIntroCandidateDao`
  - `findScoringCandidates(requesterId, loginAfter): List<ScoringCandidateRow>` — 자격 후보 전체의 경량 스코어링 행(userId·regionId·lastLoginAt + 실제 속성 + 이상형). 전체 수 = size.
  - `findRequesterProfile(requesterId): MatchScoringProfile?` — 요청자 자신의 스코어링 프로필(양방향 부합용).
  - `findDisplayProfiles(userIds: List<Long>): List<ExtraIntroCandidate>` — 상위 11명 표시 프로필.
- infra `GetExtraIntroCandidateDaoImpl`(QueryDSL): `match_user` 기준 자격 후보 + `user_details`·`user_ideal_types`(스코어링)·`region`(표시 활동지역) 조인. 재소개 제외는 `member_key` NOT EXISTS 서브쿼리.

**서비스 흐름**
1. 자격 후보 경량 행 + 요청자 프로필 + 요청자 `regionId` 적재.
2. `RegionProximityRegistry` 기반 근접 랭크 적재(신규 core out-port `GetRegionProximityPort`).
3. 공용 `MatchSelector.orderByScore`로 종합점수 정렬.
4. `totalCount = 전체 자격 후보 수`, 상위 11명 userId로 표시 프로필 적재해 순서대로 반환.

응답 DTO(api): `ExtraIntroCandidatesResponse { totalCount: Int, candidates: List<ExtraIntroCandidateResponse> }`.

## 3. 추가 소개 API — `POST /matches/v1/extra`

solomatch **command** 패키지(`@Transactional`).

- in-port `IntroduceExtraMatchUseCase.introduce(userId: Long): Match`
- `IntroduceExtraMatchService` + `@DistributedLock(prefix = EXTRA_INTRO, keys = ["#userId"], waitTime = 0)` — 더블클릭 이중 과금 fail-fast.

**흐름**
1. `getMatchUserUseCase.findByUserId(userId)` → 매칭 불가(읽기 모델 없음) 시 에러.
2. 신규 out-port `GetExtraIntroCandidatePort`로 자격 후보 경량 행 + 요청자 프로필 적재. `GetRegionProximityPort`로 근접 랭크.
3. 공용 `MatchSelector.selectBest`로 재소개 이력 없는 최고점 후보 1명 선택. 없으면 `EXTRA_INTRO_NO_CANDIDATE` 에러(코인 미차감).
4. `SpendCoinUseCase.spend(userId, SpendCoinCommand(amount = 30, coinUsageType = EXTRA_INTRO))`.
5. `Match.propose(requesterId, requesterGender, partnerId, matchType = SoloMatchType.EXTRA, now)` → `SaveMatchPort.save`.
6. 같은 트랜잭션이라 저장 실패(유니크 위반 등) 시 코인 차감도 롤백.

**응답**: `ExtraIntroResponse { matchId: Long, partnerUserId: Long }`. 프론트는 이후 `GET /matches/v1`(또는 후보 조회 재요청)으로 상대 프로필을 표시한다. (command는 도메인 모델을 반환하고, 표시는 조회 경로로 — 명령 경로에 조회를 섞지 않음)

**성능/보류**: 실시간 후보 풀 전체 스코어링은 현재 단일 인스턴스 전제에서 허용 가능. 후보 풀은 최근 로그인·재소개 제외로 자연 축소된다. 대규모 시 비용은 기존 단일 인스턴스 보류 리스크와 동일 선상.

## 4. 공용/기타 변경

- `oneulsogae-common` `CoinUsageType`에 `EXTRA_INTRO("추가 소개", 30)` 추가.
- `oneulsogae-common` `SoloMatchType`에 `EXTRA("추가 소개")` 추가.
- core `MatchErrorCode`에 `EXTRA_INTRO_NO_CANDIDATE`(소개 가능한 후보 없음) 추가. 매칭 불가는 기존 코드 재사용/필요 시 신설.
- 신규 core out-port
  - `GetExtraIntroCandidatePort`(command): 요청자 기준 자격 후보 경량 행 + 요청자 프로필.
  - `GetRegionProximityPort`: `nearbyRegionIds(regionId): List<Long>`. infra 구현이 `RegionProximityRegistry`에 위임.
- infra 어댑터: `GetExtraIntroCandidateDaoImpl`(query), `ExtraIntroCandidateAdapter`(command out-port), `RegionProximityCoreAdapter`(GetRegionProximityPort). 엔티티당 어댑터 원칙 준수.

## 5. 테스트 전략

- **알고리즘 유닛(Kotest, oneulsogae-api 테스트 소스셋 또는 matching 모듈)**: `MatchScorer`(기존 이전) + 신규 `MatchSelector`(시각·Random 고정) — 정렬·동점 무작위·재소개 제외·최고점 선택.
- **배치 회귀**: 기존 배치 E2E/유닛이 새 모듈 경로에서 그대로 통과.
- **E2E(oneulsogae-api)**:
  - 조회: 상위 11명 정렬·전체 수 정확성, 자격 필터(반대 성별·최근 로그인·재소개 제외·매칭 가능).
  - 추가 소개 성공: 코인 30 차감·`EXTRA` 매칭 `PROPOSED` 생성·이후 interest 흐름 진입.
  - 후보 없음: `EXTRA_INTRO_NO_CANDIDATE`, 코인 미차감.
  - 코인 부족: 차감 실패로 매칭 미생성(롤백).
  - 재소개 제외: 이미 소개된 상대는 후보/선택에서 제외.

## 6. 프론트엔드 안내 (백엔드에서 직접 수정하지 않음)

- `home` 도메인 `BrowserCandidateRepository.getCandidates`: mock → `GET /matches/v1/extra/candidates`. 응답이 `{ totalCount, candidates[] }` 이므로 `Candidate[]`만 쓰던 시그니처를 `{ totalCount, candidates }` 형태로 확장(또는 candidates만 사용).
- 추가 소개 액션(`CandidateListPage`): 클라이언트 무작위 선택 + `spend()` → `POST /matches/v1/extra` 호출로 교체. 응답 `matchId`로 매칭 목록 갱신/이동. 코인 차감은 서버가 수행하므로 낙관적 차감 로직 제거 또는 서버 응답 기준으로 정정.
- `Candidate` 타입: 응답 필드에 맞춰 매핑(닉네임 블러 정책은 프론트 유지).
- 전체 후보 수(`totalCount`) 표시가 필요하면 헤더/문구에 활용.
