# 설계: 팀 결성 시 추천 팀 → TeamMatch 승격

- 작성일: 2026-06-24
- 도메인: `match`
- 트리거 위치: `AcceptTeamInvitationService.accept()` (동기 · 같은 트랜잭션 · 같은 락)

## 배경 / 목표

`recommended_teams`는 **솔로 유저 1명당 1행**으로, 그 유저에게 추천된 (반대 성별·ACTIVE) 팀을 가리킨다.
두 솔로 유저 A·B가 서로 초대를 주고받아 팀 **T**를 결성(`TeamStatus.ACTIVE`)하면, 각자에게 추천됐던
개인 추천 팀을 결성된 팀 T의 실제 2:2 매칭(`TeamMatch`)으로 **승격(promote)**한다.

확인된 사실: `TeamMatchEntity`/`MatchedTeamEntity`는 정의만 있고 생성 로직이 전무하다.
본 작업은 **TeamMatch를 생성하는 최초의 경로**다.

## 핵심 의미 (확정)

팀 T(멤버 A + B)가 `ACTIVE`가 될 때:
- A의 추천 팀 Rₐ, B의 추천 팀 R_b 각각에 대해 `TeamMatch(T, R)`를 생성한다. (최대 2개)
- 추천이 없는 멤버는 스킵한다.
- A·B가 같은 팀을 추천받았으면 `distinct`로 1개만 만든다. (`member_key` 유니크 충돌 방지)
- 추천 팀 R이 해체/비활성이면 스킵한다. (**R.status == ACTIVE 일 때만** 승격)
- 원래 `recommended_teams` 행은 **그대로 둔다** (삭제·수정하지 않음).

생성되는 TeamMatch 초기 상태:
- `status = MatchStatus.PROPOSED`
- 두 팀 모두 `matched_teams.accepted = null`
- `matchType = TeamMatchType.RECOMMENDED` (신규 enum 값)
- `dateInitAmount = CoinUsageType.MEETING_INIT.coinAmount` (40)
- `dateAcceptAmount = CoinUsageType.MEETING_ACCEPT.coinAmount` (40)
- `expiresAt = now + Duration.ofDays(1)`
- `introducedDate = now.toLocalDate()`
- `memberKey = teamIds.sorted().joinToString("-")` (솔로 `Match.memberKey()` 규칙 미러링)

## 흐름 (Data Flow)

`accept()` 기존 로직(수락 처리 → `deactivateOtherInvitations` → `TeamInvitationAccepted` 발행)은
그대로 두고, 수락 결과 팀이 `ACTIVE`가 된 직후 같은 트랜잭션·락 안에서 다음을 추가한다.

1. T의 ACTIVE 멤버 userId 수집:
   `team.members.values.filter { it.status == TeamMemberStatus.ACTIVE }.map { it.userId }`
2. 각 멤버에 대해 `GetRecommendedTeamPort.findRecommendedTeamId(userId)` 조회 → null이면 스킵
3. 추천 팀 id들을 `distinct` + `!= team.id` 필터
4. 각 추천 팀 R을 `getTeamPort.findById(R)`로 로드 → `R != null && R.status == ACTIVE`일 때만 진행
5. `saveTeamMatchPort.save(TeamMatch.propose(team.id, R.id, TeamMatchType.RECOMMENDED, now))`

`now`는 이미 주입되어 있는 `timeGenerator.now()`로 얻는다.

## 컴포넌트 (추가/변경)

### meeple-common
- `TeamMatchType`에 값 추가: `RECOMMENDED("추천 팀 매칭")`

### meeple-core — `match/command/domain` (솔로 `Match` 미러링, 신규)
- `TeamMatch` (data class)
  - 필드: `id`, `matchedTeams: MatchedTeams`, `introducedDate`, `expiresAt`, `matchType: TeamMatchType`,
    `status: MatchStatus = PROPOSED`, `dateInitAmount`, `dateAcceptAmount`, `deletedAt: LocalDateTime? = null`
  - `memberKey(): String = matchedTeams.memberKey()`
  - `companion`:
    - `val EXPIRATION: Duration = Duration.ofDays(1)`
    - `fun propose(teamAId: Long, teamBId: Long, matchType: TeamMatchType, now: LocalDateTime): TeamMatch`
- `MatchedTeam` (data class): `id: Long = 0`, `teamMatchId: Long`, `teamId: Long`, `accepted: Boolean? = null`,
  `deletedAt: LocalDateTime? = null`
- `MatchedTeams` (일급 컬렉션): `values: List<MatchedTeam>`
  - `companion fun of(teamIds: List<Long>): MatchedTeams`
  - `fun memberKey(): String = values.map { it.teamId }.sorted().joinToString("-")`

### meeple-core — `match/command/application/port/out` (신규)
- `GetRecommendedTeamPort { fun findRecommendedTeamId(userId: Long): Long? }`
- `SaveTeamMatchPort { fun save(teamMatch: TeamMatch): TeamMatch }`

### meeple-core — `match/command/application`
- `AcceptTeamInvitationService`
  - 생성자에 `GetRecommendedTeamPort`, `SaveTeamMatchPort` 주입
  - private `promoteRecommendedTeams(team: Team, now: LocalDateTime)` 추가
  - `accept()` 끝에서 `if (accepted.status == TeamStatus.ACTIVE) promoteRecommendedTeams(accepted, now)` 호출

### meeple-infra — `match/command`
- `TeamMatchJpaRepository : JpaRepository<TeamMatchEntity, Long>` (신규)
- `MatchedTeamJpaRepository : JpaRepository<MatchedTeamEntity, Long>` (신규)
- `TeamMatchMapper`: `TeamMatch.toEntity()`(헤더), `TeamMatchEntity.toDomain(matchedTeams)`
- `MatchedTeamMapper`: `MatchedTeam.toEntity()`, `MatchedTeamEntity.toDomain()`
- `TeamMatchAdapter : SaveTeamMatchPort`
  - `MatchAdapter.save()` 패턴 미러링: 헤더 저장 → `id` 획득 → `matched_teams` 행들을 `copy(teamMatchId = id)`로 저장 → 도메인 재구성
- `RecommendedTeamAdapter`에 `GetRecommendedTeamPort` 구현 추가
  - `override fun findRecommendedTeamId(userId: Long): Long? = recommendedTeamJpaRepository.findByUserId(userId)?.teamId`

## 엣지 케이스

- 멤버 추천 없음 → 스킵
- A·B 동일 추천 팀 → `distinct`로 1개 (member_key 충돌 방지)
- 추천 팀 R 해체/비활성(DEACTIVATED) → 스킵 (ACTIVE만 승격)
- `member_key` 유니크: T는 방금 생성돼 기존 매치 없음 → 충돌 없음
- R은 반대 성별 팀이라 `R.id != T.id` 보장되지만, 방어적으로 `!= team.id` 필터 유지

## 테스트

### 단위 (Kotest, meeple-core)
- `TeamMatch.propose()`:
  - `memberKey`가 두 teamId 정렬 후 `"-"` join
  - `expiresAt == now + EXPIRATION`, `introducedDate == now.toLocalDate()`
  - `status == PROPOSED`, 두 `MatchedTeam`의 `accepted == null`
  - `dateInitAmount == 40`, `dateAcceptAmount == 40`, `matchType == RECOMMENDED`
- `MatchedTeams.memberKey()` 정렬/조인 동작 및 `of()` 생성

### E2E (meeple-api)
- invite → accept API 호출로 팀 결성, 사전에 두 멤버의 추천 팀(ACTIVE) 시드
- 시나리오: ①양쪽 추천 → 2매치 ②한쪽만 → 1매치 ③추천 DEACTIVATED → 스킵 ④동일 추천 → 1매치
- 검증: 현재 `team_matches` 조회 엔드포인트/리드모델이 없으므로, infra `testFixtures`에
  team_matches/matched_teams 조회 헬퍼를 추가해 픽스처 경유로 검증한다(E2E 리포지토리 직접 의존 금지 컨벤션 준수).

## 컨벤션 준수 메모

- 동일 `match` 도메인 내부 작업이라 도메인 간 참조 규칙(다른 도메인 UseCase 주입) 해당 없음.
- CQS: 본 작업은 명령 경로. `GetRecommendedTeamPort`(단건 id 조회, 잠금/상태변경 없음)는
  명령 트랜잭션 내 보조 조회로, command out-port로 둔다. (query 경로 read model과 별개)
- 엔티티당 어댑터 하나: `recommended_teams`는 기존 `RecommendedTeamAdapter` 한 곳에서
  `SaveRecommendedTeamPort`(scheduler) + `GetRecommendedTeamPort`(core)를 함께 구현.
- `TimeGenerator` 직접 `LocalDateTime.now()` 호출 금지 — 도메인엔 `now` 파라미터 주입.
- 일급 컬렉션: `MatchedTeams`로 래핑.
