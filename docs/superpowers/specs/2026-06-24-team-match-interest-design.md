# 팀 매칭 관심 요청·수락 API 설계

작성일: 2026-06-24

## 배경 / 목표

2:2 팀 매칭(`TeamMatch` 애그리거트)은 두 팀을 소개로 묶는 데까지(`PROPOSED`, 양 팀 `WAITING`)
구현되어 있으나, 두 팀이 서로 **관심을 보내고 수락해 성사(`MATCHED`)에 이르는 흐름**이 비어 있다.

이 작업은 기존 1:1 매칭의 `SendInterestService`(신청·수락 통합)를 팀 매칭 버전으로 미러링해,
팀 매칭의 관심 요청·수락 API를 추가한다.

### 제품 결정 (확정)

- **행위 주체**: 참가 팀의 ACTIVE 구성원이면 누구나 팀을 대표해 관심/수락 가능. (성사된 ACTIVE 팀은 '팀장' 구분이 없음)
- **코인 지불**: 행위한 구성원이 부담(단일 차감). 1:1 `SendInterestService`와 동일.
- **엔드포인트**: 단일 엔드포인트. 결과 상태로 신청/수락(성사)을 분기.
- **성사 후속 처리**: 양 팀 4인 그룹 채팅방 생성 + 알림 발행 포함.

## 범위

포함:
- 팀 매칭 관심 요청·수락 command 경로 전체(도메인 메서드 → 유스케이스/서비스 → 컨트롤러).
- 성사 시 코인 차감, 4인 채팅방 생성, 알림 발행.
- 도메인 유닛 테스트(Kotest) + api E2E 테스트.

미포함(범위 밖):
- 팀 매칭 조회(query) API. (별도 작업)
- 채팅 `matchId` 네임스페이스 재설계(아래 "알려진 한계" 참고).
- 일일 배치(`DAILY`)·필수 신청(`REQUIRED`) 팀 매칭 생성 경로. (이미 별도)

## 아키텍처 / 변경 사항

기존 1:1 매칭 흐름(`SendInterestService` + `Match`/`MatchMembers` + `MatchEventHandler`)을
팀 매칭 도메인 객체에 그대로 대응시킨다. 헥사고날 레이어링·CQRS 컨벤션을 따른다.

### 1. API 경계 (meeple-api)

- 신규 `TeamMatchController` (`/team-matches/v1`). `TeamMatch`는 `Team`과 별개 애그리거트라 `TeamController`와 분리한다.
- `POST /team-matches/v1/{teamMatchId}/interest` → `SendTeamInterestUseCase.sendInterest(userId, teamMatchId)`.
- 응답 `TeamMatchResponse`:
  - `teamMatchId: Long`
  - `status: MatchStatus`
  - `matchedTeams: List<{ teamId: Long, status: MatchedTeamStatus }>`

### 2. 유스케이스 / 서비스 (meeple-core, command)

`SendTeamInterestService : SendTeamInterestUseCase` — `SendInterestService`와 동일한 구조.

```
@DistributedLock(prefix = TEAM_MATCH_INTEREST, keys = ["#teamMatchId"], waitTime = 0)
@Transactional
override fun sendInterest(userId: Long, teamMatchId: Long): TeamMatch {
    val teamMatch = getTeamMatchPort.findById(teamMatchId)
        ?: throw BusinessException(TeamMatchErrorCode.TEAM_MATCH_NOT_FOUND)

    // 참가 두 팀을 로드해 행위자가 속한 팀을 식별(참가 검증 겸함)
    val teams = teamMatch.teamIds().map { getTeamPort.findById(it)!! }
    val actorTeam = teams.firstOrNull { userId in it.activeMemberIds() }
        ?: throw BusinessException(TeamMatchErrorCode.NOT_TEAM_MATCH_PARTICIPANT)

    teamMatch.validateRespondable(actorTeam.id)   // 미종료 검증
    val updated = saveTeamMatchPort.save(teamMatch.respond(actorTeam.id))

    return when (updated.status) {
        MatchStatus.MATCHED            -> completeMatch(userId, updated, teams)
        MatchStatus.PARTIALLY_ACCEPTED -> recordInterest(userId, updated, actorTeam, teams)
        else -> error("팀 관심 보내기 결과 상태가 올바르지 않습니다: ${updated.status}")
    }
}
```

- **성사(`completeMatch`)**: `MEETING_ACCEPT` 비용 차감 + 4인 채팅방 생성 + `TeamMatchAccepted` 발행.
  - 채팅방: `saveChatRoomUseCase.save(matchId = teamMatch.id, participantUserIds = 양 팀 4인)`.
    채팅방 생성은 성사의 필수 산출물이라 같은 트랜잭션에서 동기 처리(실패 시 함께 롤백).
- **신청(`recordInterest`)**: `MEETING_INIT` 비용 차감 + `TeamMatchInterestSent` 발행.
- 코인 금액은 `teamMatch.dateInitAmount`/`dateAcceptAmount`(서버 산출, 클라이언트가 정하지 않음).
- 코인 차감·상태 변경·채팅방 생성은 같은 트랜잭션. 알림만 커밋 후 best-effort.
- 다른 도메인(coin/chat/user)은 자기 out-port가 아니라 in-port(`SpendCoinUseCase`/`SaveChatRoomUseCase`/`GetUserDetailUseCase`)로 참조.

### 3. 도메인 모델 (기존 파일에 메서드 추가 — 1:1 `Match`/`MatchMembers` 미러)

`TeamMatch` (command/domain/TeamMatch.kt):
- `respond(teamId: Long): TeamMatch` — 해당 팀을 APPLY로 전이, 재계산. 전원 APPLY면 `MATCHED` + 전원 `activate()` + 만료 100년 연장.
- `validateRespondable(teamId: Long)` — 참가 팀 아님/이미 종료면 예외.
- `isParticipant(teamId: Long): Boolean`
- private `withRecomputedStatus()`, `extendExpirationForMatched()` — 1:1과 동일 의미.
- 만료 연장 상수 `MATCHED_EXPIRATION_EXTENSION_YEARS = 100L`(1:1 `Match`와 동일).

`MatchedTeams` (command/domain/MatchedTeams.kt):
- `apply(teamId: Long): MatchedTeams`
- `allApplied(): Boolean`, `anyApplied(): Boolean`
- `activateAll(): MatchedTeams`
- `isParticipant(teamId: Long): Boolean`

`MatchedTeam`: 기존 `apply()`/`activate()`/`deactivate()` 그대로 사용(추가 없음).

### 4. 이벤트 + 알림 (커밋 후 best-effort)

신규 도메인 이벤트(command/domain/event):
- `TeamMatchInterestSent(teamMatchId: Long, senderUserId: Long, recipientUserIds: List<Long>)`
  - 수신자 = 상대 팀의 ACTIVE 구성원.
- `TeamMatchAccepted(teamMatchId: Long, recipientUserIds: List<Long>)`
  - 수신자 = 양 팀 4인 중 행위자 제외(행위자는 동기 응답으로 인지).

신규 `TeamMatchEventHandler` (`MatchEventHandler`/`TeamEventHandler` 패턴):
- `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)`.
- AlarmType은 기존 **`MANY_TO_MANY_INTEREST_RECEIVED` / `MANY_TO_MANY_MATCHED`를 재사용**(신규 AlarmType 불필요).
- 문구용 닉네임은 `GetUserDetailUseCase`로 조회, 미존재 시 폴백 문구.

### 5. 아웃포트 / 어댑터 (meeple-infra)

- `GetTeamMatchPort.findById(teamMatchId: Long): TeamMatch?` **추가**(현재 `findActiveByTeamId`만 존재).
  - `TeamMatchAdapter`에 구현: 헤더 로드 + 참가 팀(`matched_teams`) 로드 후 `toDomain`.
- `SaveTeamMatchPort.save`는 기존 그대로 재사용(헤더+참가팀 upsert). 기존 행 update 경로가 정상 동작하는지 확인.

### 6. 에러 / 락 (meeple-core)

- 신규 `TeamMatchErrorCode`(core/match):
  - `TEAM_MATCH_NOT_FOUND`
  - `NOT_TEAM_MATCH_PARTICIPANT`
  - `TEAM_MATCH_ALREADY_CLOSED`
- `LockKeyConstraints.TEAM_MATCH_INTEREST` 추가(teamMatchId로 잠금, waitTime=0).
  경합 대상이 두 팀이 공유하는 "팀 매칭"이므로 userId/teamId가 아니라 teamMatchId로 잠근다.

## 데이터 흐름

1. 클라이언트가 `POST /team-matches/v1/{teamMatchId}/interest` 호출(로그인 사용자).
2. 서비스가 teamMatchId 락 획득 → 팀 매칭·참가 두 팀 로드 → 행위자 팀 식별·검증.
3. `teamMatch.respond(actorTeamId)`로 상태 전이 후 저장.
4. 결과 상태 분기:
   - `PARTIALLY_ACCEPTED`: 신청 코인 차감 → `TeamMatchInterestSent` 발행.
   - `MATCHED`: 수락 코인 차감 → 4인 채팅방 생성 → `TeamMatchAccepted` 발행.
5. 커밋 후 `TeamMatchEventHandler`가 알림 저장(best-effort).

## 에러 처리

- 팀 매칭 없음 → `TEAM_MATCH_NOT_FOUND`.
- 행위자가 두 참가 팀 어디에도 ACTIVE로 속하지 않음 → `NOT_TEAM_MATCH_PARTICIPANT`.
- 이미 종료(`MATCHED`/`CLOSED`)된 팀 매칭 → `TEAM_MATCH_ALREADY_CLOSED`.
- 같은 팀 매칭에 동시 요청 → 락 waitTime=0으로 한쪽 즉시 실패(409), 코인 이중 차감·lost update 방지.
- 코인 부족 등 차감 실패 → 같은 트랜잭션이라 상태 변경·채팅방 생성과 함께 롤백.

## 테스트

도메인 유닛(Kotest, meeple-core):
- `TeamMatch.respond`: WAITING→APPLY→`PARTIALLY_ACCEPTED`, 양 팀 APPLY→`MATCHED` + 전원 ACTIVE + 만료 연장.
- `validateRespondable`: 비참가 팀/종료 매칭 예외.
- `MatchedTeams`: `apply`/`allApplied`/`anyApplied`/`activateAll`/`isParticipant`.

api E2E(meeple-api, AbstractIntegrationSupport):
- 한 팀이 신청 → `PARTIALLY_ACCEPTED` + 신청 코인 차감 + 상대 팀 알림.
- 이어 상대 팀이 신청 → `MATCHED` + 수락 코인 차감 + 4인 채팅방 생성 + 알림.
- 비참가 사용자 호출 → 예외.

## 알려진 한계 (범위 밖)

- **채팅 `matchId` 네임스페이스 충돌 가능성**: solo `Match.id`와 `TeamMatch.id`가 별도 테이블 시퀀스라
  같은 값을 가질 수 있어 `chat_rooms.match_id` 유니크 제약과 충돌할 위험이 있다.
  단, 이는 기존 코드(`DisbandTeamService`가 `teamMatch.id`를 채팅 matchId로 사용)가 이미 채택한 컨벤션이므로
  본 작업에서 바꾸지 않고 그대로 따른다. 필요하면 별도 이슈로 분리해 채팅 매칭 키 체계를 재검토한다.
- **같은 팀 재신청 시 코인 재차감**: 이미 APPLY한 팀이 다시 신청하면 코인이 또 차감될 수 있다.
  이는 1:1 solo `SendInterestService`와 동일한 가드 수준(참가 + 미종료만)으로 미러링한 결과다.
  추가 가드는 넣지 않는다(YAGNI, solo와 일관). solo 정책이 바뀌면 함께 조정한다.
