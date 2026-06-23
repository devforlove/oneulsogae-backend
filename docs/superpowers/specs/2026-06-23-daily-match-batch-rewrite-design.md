# 거리 기반 일일 매칭 배치 재작성 설계 (인메모리 풀)

작성일: 2026-06-23
브랜치: `feat/recommended-team` (작업 브랜치 분기 예정)

## 1. 배경 / 목표

기존 일일 매칭 배치(`RunDailyMatchBatchService`)는 Redis 풀(`MatchPoolGroup`/`MatchRedisAdapter`) + `MatchIntroducer`로 구성된 레거시다. 이를 **완전히 제거**하고, 새로 도입한 `regionProximityPort`를 활용한 **인메모리 풀 기반의 가독성 높은 배치**로 재작성한다.

**동작 규칙**
- 매일 **정오(12:00, Asia/Seoul)** 자동 실행.
- 대상/후보 = **최근 2주 내 접속**(`match_user.last_login_at ≥ now − 2주`) **AND 오늘 미매칭** **AND 성사(MATCHED) 상태 아님**.
- 매칭은 **가까운 지역 순서**(`regionProximityPort.nearbyRegionIds`)로 짝짓는다.
- **재소개 방지**: 두 사람이 함께 소개된 이력이 있으면(`existsByPair`) 짝짓지 않는다.
- 한 사람은 이 배치 실행에서 **최대 1회** 매칭한다(매칭되면 풀에서 제거).

**비목표**
- 온보딩 추천 경로 변경 없음. 팀(2:2) 배치 변경 없음.
- 매칭 성사/페어링(수락) 로직 변경 없음(이 배치는 PROPOSED 소개만 생성).

## 2. 제거할 레거시 (데일리 전용 — 타 경로 의존 없음)

| 대상 | 위치 |
|---|---|
| `RunDailyMatchBatchService` | scheduler `match/command/application` |
| `MatchIntroducer` (+ `MatchIntroducerTest`) | scheduler `match/command/application` / api test |
| `MatchPoolPort`, `SaveMatchPoolPort` | scheduler `.../port/out` |
| `MatchPoolGroup` | scheduler `match/command/domain` |
| `MatchRedisAdapter` | infra `match/command/adapter` (풀 포트만 구현 → 통째 제거) |
| `GetMatchBatchTargetDao` (+ `GetMatchBatchTargetDaoImpl`) | scheduler `query/dao` / infra `match/query` |
| `GetActiveUserDao` (+ `GetActiveUserDaoImpl`) | scheduler `query/dao` / infra `user/query` |
| DTO `MatchBatchTarget`, `MatchBatchCursor`, `ActiveUser` | scheduler `query/dto` |

→ **match 배치에서 Redis를 완전히 걷어낸다.** (이들은 데일리 배치만 사용함을 확인했다)

**유지(공유)**: `RegionProximityPort`(온보딩도 사용), `TimeGenerator`(팀 배치도 사용), `SaveMatchRecordPort`, `GetMatchRecordDao.existsByPair`/`findMatchedUserIds`, `MatchBatchJob`/`MatchBatchScheduler`/`RunDailyMatchBatchUseCase`/`MatchBatchResult`.

## 3. 신규 컴포넌트

### 3.1 `MatchableUser` (read model / dto)
scheduler `match/query/dto`. `(userId: Long, gender: Gender, regionId: Long, lastLoginAt: LocalDateTime)`.
대상이자 후보. 버킷 키(`gender`,`regionId`)와 후보 정렬(`lastLoginAt`)에 쓰므로 모두 non-null.

### 3.2 `MatchPool` (순수 도메인)
scheduler `match/command/domain`. 프레임워크 비의존 → Kotest 유닛 테스트.
- 내부: `(gender, regionId)` 버킷별 후보 리스트(**최근 로그인 내림차순**) + 가용 userId 집합(`available`).
- 연산:
  - `companion fun of(users: List<MatchableUser>): MatchPool` — 버킷 구성.
  - `fun freshCandidates(gender: Gender, regionId: Long): List<MatchableUser>` — 그 버킷의 **아직 가용한** 후보(최근순).
  - `fun remove(user: MatchableUser)` — 매칭된 유저를 가용에서 제거(`available`에서 빼기 → O(1)).
  - `fun contains(user: MatchableUser): Boolean` — 아직 가용한지.
- "매칭되면 빼기"가 `available` 집합 한 곳에 모여 자명하게 읽힌다.

### 3.3 `GetMatchableUserDao` (조회) + infra 구현
scheduler `match/query/dao` + infra `match/query`(match_user를 읽으므로 match infra).
- `fun findMatchableUsers(loginAfter: LocalDateTime): List<MatchableUser>`
- 쿼리: `match_user`에서 `last_login_at ≥ :loginAfter`인 행을 `(userId, gender, regionId, lastLoginAt)`로 투영, `last_login_at` 내림차순. `idx_last_login_at_user_id` 범위 스캔.

### 3.4 `GetMatchRecordDao` 확장
scheduler `match/query/dao` + infra `match/query/GetMatchRecordDaoImpl`.
- 추가: `fun findUserIdsIntroducedOn(date: LocalDate): Set<Long>` — `solo_matches.introduced_date = :date`인 매칭의 참가자 userId 집합.
- 기존 유지: `existsByPair`(재소개 방지), `findMatchedUserIds`(성사 상태 유저).

### 3.5 `DailyMatchBatchService` (신규, `RunDailyMatchBatchUseCase` 구현)
scheduler `match/command/application`. 주입: `getMatchableUserDao`, `getMatchRecordDao`, `saveMatchRecordPort`, `regionProximityPort`, `timeGenerator`.

```
run():
  now = timeGenerator.now();  loginAfter = now.minusWeeks(2);  today = now.toLocalDate()
  regionProximityPort.refresh()                       // 근접·유저분포 스냅샷 최신화(온보딩도 이득)

  excluded: Set<Long> = findMatchedUserIds() ∪ findUserIdsIntroducedOn(today)
  matchables = findMatchableUsers(loginAfter).filterNot { it.userId in excluded }
  pool = MatchPool.of(matchables)

  for target in matchables:
      if !pool.contains(target): continue             // 이번 런에 이미 짝지어짐
      partner = findNearestFreshPartner(target, pool)  // null이면 이번엔 스킵
      if partner != null:
          saveMatchRecordPort.saveProposedMatch(target.userId, target.gender, partner.userId, now)
          pool.remove(target);  pool.remove(partner)
          recommended++

  return MatchBatchResult(targets, recommended, skipped, failed)

findNearestFreshPartner(target, pool):
  partnerGender = target.gender.opposite()
  for regionId in regionProximityPort.nearbyRegionIds(target.regionId):
      for candidate in pool.freshCandidates(partnerGender, regionId):   // 최근 로그인순
          if !getMatchRecordDao.existsByPair(target.userId, candidate.userId): return candidate
  return null
```

- 한 사용자의 실패가 다른 사용자에 전파되지 않도록 `target` 단위 try/catch로 격리하고 예외만 `failed`로 집계(기존 배치 패턴 유지).
- `saveProposedMatch`는 기존대로 `Match.propose(... DAILY ...)`로 PROPOSED 매칭을 저장(`member_key` 유니크가 동시 중복 저장을 방어).

## 4. 인덱스 / 풀스캔 회피 (요구사항: 항상 인덱스 고려, 풀스캔 회피)

| 쿼리 | 인덱스 | 비고 |
|---|---|---|
| 활성 유저 적재 `last_login_at ≥ ?` | `idx_last_login_at_user_id(last_login_at, user_id)` 범위 스캔 | 활성 집합은 본질적으로 다 읽어야 함(풀스캔 아님). `gender/region_id`는 행 조회. |
| 재소개 방지 `existsByPair` | `ux_member_key` 유니크 seek | 짝당 1 seek |
| 오늘 매칭됨 `introduced_date = today` | **신규 `idx_introduced_date`** + `ux_match_id_user_id` 조인 | 오늘 행만 seek |
| 성사 유저 `status = MATCHED` | **신규 `idx_status`** + `ux_match_id_user_id` 조인 | MATCHED 행만 seek(현재는 스캔) |
| 짝 후보 선택 | 인메모리 버킷 | 추가 쿼리 없음 |

**신규 인덱스 2개** (`solo_matches`): `idx_introduced_date(introduced_date)`, `idx_status(status)`. `docs/migration/*.sql`로 명시 적용(ddl-auto 환경). 쓰기 비용은 매칭 생성 시 인덱스 2개 갱신 — 일일 배치/제외 조회의 풀스캔 회피 이득과 맞바꾼다.

## 5. 스케줄 변경

`meeple-api/src/main/resources/application.yml`의 매칭 배치 cron 기본값을 **정오 1회**로 변경:
```yaml
meeple:
  match:
    batch:
      cron: ${MEEPLE_MATCH_BATCH_CRON:0 0 12 * * *}   # 매일 12:00:00 (Asia/Seoul)
```
`MatchBatchScheduler`(`@Scheduled(zone="Asia/Seoul")`)·`MatchBatchJob`(동시 실행 가드)는 그대로. 환경변수 override 유지.

## 6. 테스트 전략

- **`MatchPool` 순수 → Kotest 유닛**: 버킷팅, `freshCandidates`(가용/최근순), `remove`(가용 제외), `contains`.
- **`DailyMatchBatchService` → 유닛**(가짜 dao/port): 가까운 지역 우선 짝, 재소개/오늘매칭/성사 유저 제외, 같은 런 이중 매칭 방지, 후보 없음 시 스킵.
- **`RunDailyMatchBatchIntegrationTest` 재작성**(meeple-api E2E, Testcontainers): Redis 정리가 사라져 단순화. 기존 시나리오(같은 지역 PROPOSED·DAILY / 후보 없음 / 성사 제외 / 이중 매칭 방지 / 근접 우선) + **"오늘 매칭된 유저 제외"** 시나리오 추가.
- `MatchIntroducerTest` **삭제**. `AdminMatchBatchE2ETest` 유지(엔드포인트 → Job 경로).

## 7. 가독성 원칙 (요구사항: 가독성 최우선)

- 매칭 로직이 **"풀에서 가까운 후보를 꺼내 짝짓고 빼기"**라는 한 문장으로 읽히도록, 상태(가용 집합)는 `MatchPool` 한 곳에만 둔다.
- 제외 규칙은 **서비스 상단에서 집합 연산**(`excluded = matched ∪ introducedToday`)으로 한눈에 보이게 한다.
- 타입 명시·`TimeGenerator` 주입·일급 컬렉션 등 CLAUDE.md 규칙 준수.

## 8. 영향 파일 (참고, 계획 단계 확정)

- 제거: §2 목록.
- 신규: `MatchableUser`(dto), `MatchPool`(domain), `GetMatchableUserDao`(+infra impl), `DailyMatchBatchService`, `findUserIdsIntroducedOn`(dao+impl), 인덱스 SQL.
- 수정: `GetMatchRecordDao`(+impl) 메서드 추가, `SoloMatchEntity` 인덱스, `application.yml` cron, 통합 테스트 재작성.
- 유지: `RegionProximityPort`, `TimeGenerator`, `SaveMatchRecordPort`, `MatchBatchJob`, `MatchBatchScheduler`, `RunDailyMatchBatchUseCase`, `MatchBatchResult`.
