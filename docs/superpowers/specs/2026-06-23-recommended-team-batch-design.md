# 근접 팀 추천 일일 배치 (RecommendedTeamBatch) 설계

작성일: 2026-06-23

## 배경 / 목표

미팅 탭의 팀-팀 매칭 대신, **팀 미소속 솔로 유저에게 가까운 미팅 팀을 랜덤 추천**하는 일일 배치로 전환한다.
기존 `RunRecommendTeamBatchUseCase`(반대 성별 + **같은 권역 코드** 팀 1개 적재)는 **완전히 제거**하고,
솔로 1:1 매칭 배치(`SoloMatchBatchService`)와 **동일한 방식**(근접 권역 셔플 + 전체 1회 적재 + 인메모리 제외)으로 재구현한다.

### 확정된 요구사항

- **대상**: 팀 미소속 솔로 유저 (`match_user`에 있으나 비삭제 `team_members`가 없는 유저)
- **성별**: 유저와 **반대 성별** 팀만 추천 (팀은 동성 구성 → `teamGender = user.gender.opposite()`)
- **근접**: 유저 `regionId` 기준 `RegionProximityPort.nearbyRegionIds` → 상위 10권역 셔플(`RegionShuffler`) → 팀이 있는 첫 권역에서 **랜덤 팀 1개**
- **팀의 권역**: `teams.region_id`(팀 활동지역, non-null)를 사용한다. (기존처럼 팀원 `region_code`를 EXISTS로 보지 않는다)
- **추천 개수**: 유저당 1개. `recommended_teams`의 `UNIQUE(user_id)` 유지, upsert(교체).
- **하루 1회**: 같은 날 재실행 시 이미 추천된 유저는 다시 추천하지 않는다(멱등).
- **이전 내역 무시**: 솔로 배치의 재소개 회피(`existsByPair`)·성사 제외 같은 이력 필터를 두지 않는다. 과거 추천/매칭이 있어도 추천한다.

## 솔로 배치와 동일한 실행 방식

`SoloMatchBatchService.run()`과 같은 골격:

1. `regionProximityPort.refresh()`로 근접 스냅샷 최신화.
2. **인메모리 제외 집합**: `getRecommendedTeamRecordDao.findUserIdsRecommendedOn(today)` → 오늘 이미 추천받은 유저 id 집합.
3. **대상 전체 1회 적재**: `getRecommendableSoloUserDao.findRecommendableSoloUsers()` 후 제외 집합으로 `filterNot`. (키셋 페이징 제거 — 솔로 배치와 동일)
4. **후보 팀 풀 1회 적재**: `getCandidateTeamDao.findCandidateTeams()` → `TeamPool.of(...)`. 팀은 여러 유저에게 중복 추천 가능하므로 풀에서 제거하지 않는다(읽기 전용).
5. 대상 순회: 각 유저에 대해 셔플된 근접 권역을 돌며 첫 후보 권역의 랜덤 팀을 골라 `saveRecommendedTeamPort.replace(userId, teamId, today)`. 후보 없으면 skip.
6. 유저 단위 try-catch 격리, 예외만 `failed` 집계. `RecommendedTeamBatchResult(targets, recommended, skipped, failed)` 반환·로깅.

```kotlin
// 의사코드 (SoloMatchBatchService와 대칭)
override fun run(): RecommendedTeamBatchResult {
    val today: LocalDate = timeGenerator.today()
    regionProximityPort.refresh()

    val excluded: Set<Long> = getRecommendedTeamRecordDao.findUserIdsRecommendedOn(today)
    val targets: List<RecommendableSoloUser> = getRecommendableSoloUserDao.findRecommendableSoloUsers()
        .filterNot { user: RecommendableSoloUser -> user.userId in excluded }
    val pool: TeamPool = TeamPool.of(getCandidateTeamDao.findCandidateTeams())

    var recommended = 0; var skipped = 0; var failed = 0
    for (target: RecommendableSoloUser in targets) {
        try {
            val teamId: Long? = findNearestRandomTeam(target, pool)
            if (teamId == null) { skipped++; continue }
            saveRecommendedTeamPort.replace(target.userId, teamId, today)
            recommended++
        } catch (e: Exception) { failed++; log.warn("팀 추천 배치 처리 실패 userId={}", target.userId, e) }
    }
    return RecommendedTeamBatchResult(targets.size, recommended, skipped, failed)
}

private fun findNearestRandomTeam(target: RecommendableSoloUser, pool: TeamPool): Long? {
    val teamGender: Gender = target.gender.opposite()
    val regionOrder: List<Long> = regionShuffler.shuffleNearest(regionProximityPort.nearbyRegionIds(target.regionId))
    for (regionId: Long in regionOrder) {
        val teamIds: List<Long> = pool.teamIdsOf(teamGender, regionId)
        if (teamIds.isNotEmpty()) return teamIds.random(random)
    }
    return null
}
```

권역 내 랜덤은 서비스에 `random: Random = Random.Default`를 주입한다(`RandomRegionShuffler`와 동일 패턴, 테스트 시드 고정).

## 컴포넌트 변경

### oneulsogae-scheduler

| 구분 | 대상 |
|---|---|
| 🗑️ 제거 | `port/in/RunRecommendTeamBatchUseCase` |
| 🗑️ 제거 | `application/RunRecommendTeamBatchService` |
| ✨ 신규 | `port/in/RunRecommendedTeamBatchUseCase` (`fun run(): RecommendedTeamBatchResult`) |
| ✨ 신규 | `application/RecommendedTeamBatchService` (impl) |
| ✨ 신규 | `command/domain/TeamPool` — `(gender, regionId)` 버킷, `teamIdsOf(gender, regionId): List<Long>` |
| ✨ 신규 | `query/dto/CandidateTeam(teamId, gender, regionId)` |
| ✨ 신규 | `query/dao/GetRecommendedTeamRecordDao.findUserIdsRecommendedOn(date): Set<Long>` |
| ♻️ 개명 | `command/domain/RecommendTeamBatchResult` → `RecommendedTeamBatchResult` |
| 🔧 변경 | `query/dto/RecommendableSoloUser`: `regionCode: Int` → `regionId: Long` |
| 🔧 변경 | `query/dao/GetRecommendableSoloUserDao`: `findTargets(cursor, limit)` → `findRecommendableSoloUsers(): List<RecommendableSoloUser>` |
| 🔧 변경 | `query/dao/GetCandidateTeamDao`: `findOneCandidateTeamId(...)` → `findCandidateTeams(): List<CandidateTeam>` |
| ♻️ 개명 | `command/adapter/RecommendTeamBatchJob` → `RecommendedTeamBatchJob` (의존 in-port 교체) |
| ♻️ 재사용 | `RegionProximityPort`, `RegionShuffler`, `SaveRecommendedTeamPort`, `TimeGenerator` |

### oneulsogae-infra

| 구분 | 대상 |
|---|---|
| 🔧 변경 | `query/GetRecommendableSoloUserDaoImpl`: `regionId` 투영 + 전체 반환(키셋 제거). NOT EXISTS(team_members)는 유지 |
| 🔧 변경 | `query/GetCandidateTeamDaoImpl`: `teams`(status=ACTIVE)에서 `(id, gender, region_id)` 전체 반환. 멤버 region_code EXISTS·랜덤 offset 제거 |
| ✨ 신규 | `query/GetRecommendedTeamRecordDaoImpl`: `recommended_teams`에서 `recommended_date = date and deleted_at is null`인 `user_id` 집합 |
| ♻️ 그대로 | `command/adapter/RecommendedTeamAdapter.replace` (upsert 변경 없음) |

### oneulsogae-api

| 구분 | 대상 |
|---|---|
| ♻️ 개명 | `scheduler/match/RecommendTeamBatchScheduler` → `RecommendedTeamBatchScheduler` |
| ♻️ 개명 | `api/admin/AdminRecommendTeamBatchController` → `AdminRecommendedTeamBatchController` (HTTP 경로 `/admin/v1/teams/recommend-batch`는 **유지**) |
| ♻️ 개명 | `api/admin/response/RecommendTeamBatchResponse` → `RecommendedTeamBatchResponse` |
| 🔧 변경 | `application.yml`: `oneulsogae.match.recommend-team-batch.cron` → `recommended-team-batch.cron`, env `ONEULSOGAE_RECOMMEND_TEAM_BATCH_CRON` → `ONEULSOGAE_RECOMMENDED_TEAM_BATCH_CRON` (기본 `0 0 4 * * *` 유지) |

> 개명은 사용자가 요청한 `RecommendedTeamBatch` 네이밍 통일을 위한 것. HTTP 엔드포인트 경로와 cron 기본값은 바꾸지 않는다.

## 성능 / 인덱스

- **후보 팀 풀**: ACTIVE 팀은 유저보다 훨씬 적으므로 전체 1회 로드 후 인메모리 버킷팅(솔로 배치의 `MatchPool`과 동일 비용 구조).
- **대상 조회**: `match_user` 베이스 + `team_members` NOT EXISTS. 대상 전체를 1회 로드(솔로 배치와 동일).
- **오늘 추천분 제외**: `recommended_teams`를 `recommended_date`로 조회. 같은 날 첫 실행이면 결과 0건. 재실행 시에만 비용 발생. `recommended_date` 단일 조건 조회이므로 `(recommended_date)` 인덱스가 있으면 seek, 없으면 풀스캔이나 추천 테이블 규모(유저당 1행)상 허용 범위. **`recommended_date` 인덱스 유무를 확인하고 없으면 추가를 검토**한다.
- 팀 권역을 `teams.region_id`로 직접 읽어 기존의 `team_members`↔`match_user` EXISTS 조인을 제거 → 후보 조회 단순화·경량화.

## 테스트 (솔로 배치와 동일 전략)

- **유닛(Kotest, `oneulsogae-api/src/test/.../scheduler/match/`)**: `TeamPoolTest` — `teamIdsOf`가 같은 `(gender, regionId)` 버킷의 teamId를 반환하고 다른 성별/권역과 섞이지 않음. (`MatchPoolTest` 패턴)
- **통합(`oneulsogae-api/src/test/.../api/scheduler/`)**: `RunRecommendedTeamBatchIntegrationTest` — 실 컨텍스트 + Testcontainers. `TestRegionShufflerConfig`(항등 셔플)로 근접 우선이 결정적. 시나리오:
  - 반대 성별·가까운 권역 ACTIVE 팀 → 그 팀 추천 적재
  - 반대 성별 후보 팀 없음 → skip
  - 가까운/먼 권역 모두 후보 → 가까운 권역 팀 추천
  - 오늘 이미 추천된 유저 → 재실행해도 제외(추천 1행 유지)
  - 팀 미소속 조건: 팀에 속한 유저는 대상 아님
- **E2E**: `AdminRecommendTeamBatchE2ETest` → `AdminRecommendedTeamBatchE2ETest`로 개명·갱신. 기존 "같은 권역" 문구를 "가까운 권역"으로, 검증을 새 동작에 맞춘다. 권한(403/401)·재실행 1행 유지 케이스는 유지.

## 영향 없는 것 / 비범위

- `recommended_teams` 스키마·`RecommendedTeamAdapter`·표시용 조회(`GetRecommendedTeamDaoImpl`)는 변경하지 않는다.
- 팀-팀 매칭, 팀 결성/초대 흐름은 건드리지 않는다.
- HTTP 엔드포인트 경로, cron 기본 시각(04:00)은 유지한다.
