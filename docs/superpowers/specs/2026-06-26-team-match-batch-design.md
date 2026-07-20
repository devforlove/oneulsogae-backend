# TeamMatchBatchJob 설계

## 목표

2:2 팀 매칭을 위한 일일 배치 `TeamMatchBatchJob`을 `oneulsogae-scheduler`에 추가한다.
기존 `SoloMatchBatchJob`(1:1)과 동일한 골격으로, **결성(ACTIVE) 팀끼리** 지역 근접 기반으로 소개(`team_matches` PROPOSED)를 만든다.

## 요구사항 (확정)

1. **후보 팀 조건**: 팀이 `ACTIVE`이고, 그 팀의 `ACTIVE` 구성원 중 **한 명이라도 최근 2주 이내 로그인**.
2. **제외**:
   - 이미 성사(`MATCHED`)된 팀 매칭에 속한 팀.
   - 오늘 이미 소개된(`team_matches.introduced_date = today`) 팀.
   - 같은 팀 조합으로 과거에 소개된 이력(`team_matches.member_key`)이 있으면 그 조합은 건너뜀.
3. **매칭 규칙**: 반대 성별 팀끼리(팀은 동성 구성). 지역 근접순으로 가까운 권역부터, 최근접 상위 N권역은 순서를 섞는다(`RegionShuffler`). 같은 권역 내 후보가 여럿이면 **팀 최근 로그인순**(구성원 최신 로그인 시각이 빠른 팀 우선).
4. **저장**: 성사 후보 한 쌍을 `TeamMatch.propose(teamA, teamB, DAILY, now)`로 `team_matches`(PROPOSED) + `matched_teams`(WAITING)에 적재.
5. **풀 1회 매칭**: 한 팀은 이번 실행에서 한 번만 매칭한다(매칭되면 양 팀을 풀에서 제거 — solo `MatchPool`과 동일).
6. **크론**: 매일 13:00 (Asia/Seoul), `ONEULSOGAE_TEAM_MATCH_BATCH_CRON`로 덮어쓰기, local 프로파일은 1분마다.

## 아키텍처 (solo 미러링)

`oneulsogae-scheduler`는 core에 비의존 → 자체 포트/DTO를 정의하고, core 도메인을 아는 `oneulsogae-infra` 어댑터가 구현을 잇는다.

### oneulsogae-scheduler (신규)
- `query/dto/MatchableTeam.kt` — `(teamId, gender, regionId, lastLoginAt)` 읽기 모델. `lastLoginAt` = 팀 구성원 최신 로그인.
- `query/dto/MatchedTeamIds.kt` — `Set<Long>` 일급 컬렉션.
- `query/dao/GetMatchableTeamDao.kt` — `findMatchableTeams(loginAfter): List<MatchableTeam>`.
- `query/dao/GetTeamMatchRecordDao.kt` — `existsByPair`, `findMatchedTeamIds()`, `findTeamIdsIntroducedOn(date)`.
- `command/domain/TeamMatchBatchResult.kt` — `(targets, recommended, skipped, failed)`.
- `command/domain/TeamMatchPool.kt` — `(gender, regionId)` 버킷(최근 로그인 desc) + 가용 teamId 집합 + `remove`. (solo `MatchPool` 미러; 읽기전용 `TeamPool`과 별개)
- `command/application/port/in/RunTeamMatchBatchUseCase.kt` — `run(): TeamMatchBatchResult`.
- `command/application/port/out/SaveTeamMatchRecordPort.kt` — `saveProposedTeamMatch(teamAId, teamBId, now)`.
- `command/application/TeamMatchBatchService.kt` — `RunTeamMatchBatchUseCase` 구현. (`RegionProximityPort`/`RegionShuffler`/`TimeGenerator` 재사용)
- `command/adapter/TeamMatchBatchJob.kt` — 진입점 + `AtomicBoolean` 중복 실행 가드.

### oneulsogae-infra (신규/확장)
- `match/query/GetMatchableTeamDaoImpl.kt` — QueryDSL. `teams(status=ACTIVE)` ⋈ `team_members(status=ACTIVE)` ⋈ `match_user(userId)`, 팀별 group by, `max(last_login_at) >= loginAfter` having, select `team.id, team.gender, team.regionId, max(last_login_at)`.
- `match/query/GetTeamMatchRecordDaoImpl.kt` — QueryDSL. `existsByPair`(member_key 동등), `findMatchedTeamIds`(MATCHED 헤더 ⋈ ACTIVE matched_teams), `findTeamIdsIntroducedOn`(introduced_date 동등).
- `match/command/adapter/TeamMatchAdapter.kt` — `SaveTeamMatchRecordPort` 추가 구현 → `save(TeamMatch.propose(..., DAILY, now))`. (TeamMatchEntity 어댑터 1개 유지)

### oneulsogae-api (신규)
- `scheduler/match/TeamMatchBatchScheduler.kt` — `@Scheduled(cron = "${oneulsogae.match.team-match-batch.cron}")` → `TeamMatchBatchJob.run()`.
- `application.yml` — `oneulsogae.match.team-match-batch.cron` 기본 `0 0 13 * * *`, local 1분.

## 매칭 알고리즘 (TeamMatchBatchService.run)

```
now = timeGenerator.now(); loginAfter = now - 2주; today = now.date
regionProximityPort.refresh()
excluded = findMatchedTeamIds() + findTeamIdsIntroducedOn(today)
matchables = findMatchableTeams(loginAfter).filterNot { it.teamId in excluded }
pool = TeamMatchPool.of(matchables)
for target in matchables:
    if target not in pool: continue
    partner = findNearestFreshPartner(target, pool)   // 반대성별, 근접권역 셔플, member_key 미소개 첫 후보
    if partner == null: skipped++; continue
    saveProposedTeamMatch(target.teamId, partner.teamId, now)
    pool.remove(target); pool.remove(partner); recommended++
```

## 테스트

`oneulsogae-api` 통합 테스트 `RunTeamMatchBatchIntegrationTest` (Testcontainers, `RunRecommendedTeamBatchIntegrationTest` 미러):
- 반대 성별·근접 권역의 ACTIVE 팀 둘(각 구성원 2주 내 로그인) → `team_matches` PROPOSED 1건 생성.
- 구성원이 2주 내 로그인 안 함 → 제외.
- 팀이 ACTIVE 아님 → 제외.
- 같은 성별만 존재 → 매칭 없음.
- 근/원 권역 후보 → 근접 권역 팀 선택.
- 이미 MATCHED 팀 → 제외.
- 오늘 이미 소개된 팀 → 제외(재실행 멱등).

(현재 scheduler 모듈에 단위 테스트가 없어 solo와 동일하게 api 통합 테스트로 검증)

## 비범위

- 관리자 수동 트리거 엔드포인트(요청 없음).
- `meeple-frontend` 변경(별도 안내). 응답/엔티티 추가가 없으므로 프론트 영향 없음.
