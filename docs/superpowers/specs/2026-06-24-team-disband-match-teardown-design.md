# 팀 해체 시 매칭 종료·채팅 차단·상대 알림 설계

작성일: 2026-06-24
브랜치: feat/team-match-promotion

> **정정 (2026-06-24):** 알림 수신자는 "상대 팀(매칭된 상대) 구성원"이 아니라 **해체를 실행한 구성원을 제외한 같은 팀의 남은 구성원**이다.
> 아래 본문의 "상대 팀 알림" 관련 서술은 이 정정으로 대체된다. 매칭 정리(미성사 CLOSED 종료 / MATCHED 유지 + 나간 팀원 채팅 차단)는 그대로다.
> 구현 반영: 이벤트 `TeamDisbanded`, 알림타입 `TEAM_DISBANDED("팀 해체됨")`, 문구 "함께하던 팀이 해체되었어요."

## 배경 / 목표

`DisbandTeamService.disband(userId, teamId)`는 현재 팀과 팀원만 비활성화(`TeamStatus.DEACTIVATED`)하고, 그 팀이 참여 중이던
2:2 팀 매칭(`team_matches` / `matched_teams`)과 채팅방은 손대지 않는다. 그래서 한 팀이 매칭 도중 해체되면 상대 팀 쪽 매칭/채팅
데이터가 정합성 없이 남는다.

이 작업은 팀 해체 시 다음을 함께 처리한다.

1. **상대 팀에게 알림 발송** (원 요구사항).
2. **아직 성사되지 않은 매칭(PROPOSED / PARTIALLY_ACCEPTED)은 종료**(`CLOSED`).
3. **성사된 매칭(MATCHED)은 그대로 유지**하되, 나가는 팀원이 채팅방에 들어가지 못하도록 해당 팀원의
   `chatroom_member`만 비활성화(`DEACTIVE`)한다. 채팅방 자체는 닫지 않는다.

## 매칭 상태별 분기

disband 팀이 참여한 `status != CLOSED`인 team_match 각각에 대해:

| 매칭 상태 | 매칭 처리 | 채팅 처리 | 상대 알림 |
|---|---|---|---|
| MATCHED (성사됨) | 그대로 유지 | 나가는 팀원의 `chatroom_member` → DEACTIVE (방 유지) | 발송 |
| PROPOSED / PARTIALLY_ACCEPTED | `close()` (CLOSED + matchedTeams DEACTIVE) | 해당 없음(채팅방 미생성) | 발송 |

## 트랜잭션 흐름 (`DisbandTeamService.disband`, 단일 트랜잭션 → 원자적)

```
team = getTeamPort.findById(teamId) ?: throw TEAM_NOT_FOUND
leavingMemberIds = team.activeMemberIds()              // 해체 전 캡처
saveTeamPort.save(team.disband(userId, now))           // 기존: 팀/팀원 비활성화

matches = getTeamMatchPort.findActiveByTeamId(teamId)  // status != CLOSED (신규 포트)
recipients = []
for m in matches:
    recipients += getTeamPort.findById(m.opponentTeamIdOf(teamId))?.activeMemberIds().orEmpty()
    if m.isMatched():
        deactivateChatRoomMemberUseCase.deactivate(m.id, leavingMemberIds)   // 채팅 입장 차단
    else:
        saveTeamMatchPort.save(m.close())                                    // 미성사 매칭 종료

if recipients.isNotEmpty():
    domainEventPublisher.publish(OpponentsNotifiedOnDisband(teamId, recipients.distinct()))
return savedTeam
```

커밋 후 `TeamEventHandler`가 `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)`로
알림을 발송한다(기존 알림 패턴과 동일, 베스트에포트).

## 변경 / 추가 목록

### match 도메인 (meeple-core)
- `TeamMatch.close()` = `copy(status = CLOSED, matchedTeams = matchedTeams.deactivateAll())` (소프트삭제 안 함, 기록 보존)
- `TeamMatch.isMatched()` = `status == MatchStatus.MATCHED`
- `TeamMatch.opponentTeamIdOf(teamId): Long` (또는 `MatchedTeams.opponentTeamIdOf`)
- `MatchedTeams.deactivateAll(): MatchedTeams`
- 이벤트 `OpponentsNotifiedOnDisband(disbandedTeamId: Long, recipientUserIds: List<Long>)`
  (match/command/domain/event)

### match 아웃포트 / 어댑터 (meeple-core / meeple-infra)
- 신규 out-port `GetTeamMatchPort.findActiveByTeamId(teamId: Long): List<TeamMatch>` (command 전용, 쿼리 dao 미재사용 = CQRS)
- `TeamMatchAdapter`가 구현. 파생 쿼리 조합:
  - `matchedTeamJpaRepository.findByTeamId(teamId)` → teamMatchIds
  - `teamMatchJpaRepository.findByIdInAndStatusNot(ids, CLOSED)`
  - `matchedTeamJpaRepository.findByTeamMatchIdIn(teamMatchIds)` → 양 팀 묶어 `MatchedTeams` 조립
  - 인덱스: `idx_team_id`, PK IN, `ux_team_match_id_team_id` 선두 seek로 받쳐짐(풀스캔 없음)

### chat 도메인 (meeple-core) — 신규 in-port
- `DeactivateChatRoomMemberUseCase.deactivate(matchId: Long, userIds: List<Long>)`
- `DeactivateChatRoomMemberService`(@Transactional):
  ```
  val room = getChatRoomPort.findByMatchId(matchId) ?: return       // 방 없으면 no-op
  val members = getChatRoomMemberPort.findAllByChatRoomId(room.id)
  saveChatRoomMemberPort.saveAll(members.deactivate(userIds.toSet()))
  ```
- 일급 컬렉션 `ChatRoomMembers.deactivate(userIds: Set<Long>): ChatRoomMembers`
  = 대상 userId 멤버만 `deactivate()`해서 반환

### 알림 (meeple-common / meeple-core)
- `AlarmType.MANY_TO_MANY_OPPONENT_DISBANDED("상대 팀 해체")` 추가
- `TeamEventHandler.onOpponentsNotifiedOnDisband(event)`:
  수신자별 `SaveAlarmCommand(userId, type = MANY_TO_MANY_OPPONENT_DISBANDED,
  title = "상대 팀 해체", description = "상대 팀이 해체되었어요.", fromTeamId = disbandedTeamId)`

### 서비스
- `DisbandTeamService`에 `getTeamMatchPort`, `saveTeamMatchPort`, `domainEventPublisher`,
  `deactivateChatRoomMemberUseCase` 주입 + 위 분기 로직 추가 (호출자 1개라 별도 UseCase로 분리하지 않고 inline)

## 가정 / 결정

- **MATCHED 유지 시 disband 팀의 `matched_teams` 행도 그대로 둔다**(매칭 기록 보존). 팀 자체는 `DEACTIVATED`.
- **MATCHED 매칭의 상대 팀에게도 알림을 발송**한다(원 요구사항인 "상대 알림" 유지).
- 팀 2:2 매칭의 채팅방 생성(성사 B/C)은 아직 미구현이므로 현재 `findByMatchId(teamMatch.id)`는 대개 null →
  채팅 차단은 no-op. `ChatRoom.matchId == TeamMatch.id` 연결을 전제로 **전방 호환**되게 작성한다.
- `@DistributedLock`은 기존대로 disband 대상 teamId에만 건다. 상대 팀/매칭에 대한 동시성은 잠그지 않는다
  (팀 매칭 성사 흐름이 미구현이라 현재 경합 위험이 낮음). 추후 성사 흐름 구현 시 재검토.

## 테스트

- 도메인 유닛(Kotest): `TeamMatch.close()/isMatched()/opponentTeamIdOf()`, `MatchedTeams.deactivateAll()`,
  `ChatRoomMembers.deactivate(userIds)`
- E2E(meeple-api, AbstractIntegrationSupport):
  - MATCHED 매칭 + 채팅방 보유 팀 해체 → 매칭 유지, 나간 팀원 `chatroom_member` DEACTIVE, 상대 팀원 알림 row 생성
  - 미성사(PROPOSED/PARTIALLY_ACCEPTED) 매칭 보유 팀 해체 → 매칭 CLOSED + matched_teams DEACTIVE, 상대 팀원 알림 row 생성
