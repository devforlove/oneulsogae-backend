# 팀 매칭 종료 API 설계

## 배경 / 목표

성사(MATCHED)된 2:2 팀 매칭을 한 팀이 종료할 수 있는 API를 추가한다.
로직은 1:1 매칭 종료(`SoloMatchController` `DELETE /matches/v1/{matchId}` → `EndMatchService`)를 미러하되, 참가 단위가 "사용자"가 아니라 "팀"이라는 점만 다르다.

종료 범위는 1:1과 동일하게 **"나간 팀"에 한정**한다.
- 나간 팀의 `matched_team`만 비활성(DEACTIVE) + soft delete.
- 상대 팀의 `matched_team`과 `team_matches` 헤더는 유지된다(상대 팀도 나중에 따로 종료 가능).
- 단, 상대 팀이 이미 나간 상태에서 마지막 팀이 종료하면 `team_matches` 헤더까지 CLOSED + soft delete (1:1 `Match.leave`와 동일).

## 요구사항 (요청 4단계)

1. 유효한 요청인지 검증.
2. 나간 팀의 `matched_team` 비활성화 + soft delete.
3. 나간 팀 **팀원 전원**의 채팅방 멤버 비활성화.
4. 상대 팀에 "매칭이 종료되었다" 알림 전달.

## 변경 대상

### 1. HTTP 경계 — `oneulsogae-api`

`TeamMatchController`에 엔드포인트 추가:

```
DELETE /team-matches/v1/{teamMatchId}  → ApiResponse<Unit>
```

- `@LoginUser user: AuthUser`로 `userId` 추출.
- `endTeamMatchUseCase.endTeamMatch(user.id, teamMatchId)` 위임 후 `ApiResponse.success()`.
- 컨트롤러에 `EndTeamMatchUseCase` 주입 추가.

### 2. in-port — `oneulsogae-core`

`com.org.oneulsogae.core.match.command.application.port.in.EndTeamMatchUseCase`

```kotlin
interface EndTeamMatchUseCase {
    /** [userId]가 속한 팀이 [teamMatchId] 팀 매칭을 종료한다. (참가·성사 검증 → 내 팀 비활성/soft delete + 우리 팀원 채팅 비활성 + 상대 팀 알림) */
    fun endTeamMatch(userId: Long, teamMatchId: Long)
}
```

### 3. Service — `oneulsogae-core`

`com.org.oneulsogae.core.match.command.application.EndTeamMatchService`

`EndMatchService`(흐름) + `SendTeamInterestService`(행위자 팀 식별)를 합친 구조.

주입: `GetTeamMatchPort`, `SaveTeamMatchPort`, `GetTeamPort`, `DeactivateChatRoomMemberUseCase`, `DomainEventPublisher`, `TimeGenerator`.

```kotlin
@DistributedLock(prefix = LockKeyConstraints.TEAM_MATCH_INTEREST, keys = ["#teamMatchId"], waitTime = 0)
@Transactional
override fun endTeamMatch(userId: Long, teamMatchId: Long) {
    val teamMatch: TeamMatch = getTeamMatchPort.findById(teamMatchId)
        ?: throw BusinessException(TeamMatchErrorCode.TEAM_MATCH_NOT_FOUND)

    // 참가 두 팀을 로드해 행위자가 ACTIVE 구성원으로 속한 팀을 식별 (참가 검증 겸함)
    val teams: Teams = Teams(teamMatch.matchedTeams.teamIds().mapNotNull { teamId: Long -> getTeamPort.findById(teamId) })
    val actorTeam: Team = teams.findByActiveMember(userId)
        ?: throw BusinessException(TeamMatchErrorCode.NOT_TEAM_MATCH_PARTICIPANT)

    teamMatch.validateTerminable(actorTeam.id)

    val now: LocalDateTime = timeGenerator.now()
    // 상대 팀이 이미 나갔는지(= 알릴 상대가 없는 마지막 종료인지)를 leave 전에 판단
    val isLastTeam: Boolean = teamMatch.isLastActiveTeam(actorTeam.id)

    saveTeamMatchPort.save(teamMatch.leave(actorTeam.id, now))

    // 우리 팀원 전원을 채팅방에서 비활성화 + 남는 상대 팀에 나감 안내 메세지(기존 TEAM 분기 재사용)
    deactivateChatRoomMemberUseCase.deactivate(ChatRoomMatchType.TEAM, teamMatchId, actorTeam.activeMemberIds())

    if (!isLastTeam) {
        domainEventPublisher.publish(
            TeamMatchEnded(
                teamMatchId = teamMatchId,
                fromTeamId = actorTeam.id,
                recipientUserIds = teams.opponentActiveMemberIds(actorTeam.id),
            ),
        )
    }
}
```

트랜잭션 경계는 1:1과 동일: 매칭 상태 변경 + 채팅 처리는 한 트랜잭션(원자성), 알림만 커밋 후 best-effort.

### 4. 도메인 모델 — `oneulsogae-core` (1:1 `Match` 미러)

`TeamMatch`:

```kotlin
/** [teamId] 팀이 종료할 수 있는 상태인지 검증한다. */
fun validateTerminable(teamId: Long) {
    if (!isParticipant(teamId)) throw BusinessException(TeamMatchErrorCode.NOT_TEAM_MATCH_PARTICIPANT)
    if (status == MatchStatus.CLOSED) throw BusinessException(TeamMatchErrorCode.TEAM_MATCH_ALREADY_CLOSED)
    if (status != MatchStatus.MATCHED) throw BusinessException(TeamMatchErrorCode.TEAM_MATCH_NOT_MATCHED)
}

/** [teamId]의 상대 팀이 모두 비활성인지 여부. (이 팀이 나가면 알릴 상대가 없는 마지막 종료) */
fun isLastActiveTeam(teamId: Long): Boolean =
    matchedTeams.isLastActiveTeam(teamId)

/**
 * [teamId] 팀이 이 매칭을 나간 새 모델을 반환한다.
 * 내 팀 matched_team만 DEACTIVE + soft delete 하되, 상대 팀도 모두 비활성이면(마지막 종료) 헤더까지 CLOSED + soft delete.
 */
fun leave(teamId: Long, now: LocalDateTime): TeamMatch {
    val left: TeamMatch = copy(matchedTeams = matchedTeams.leave(teamId, now))
    return if (left.matchedTeams.allDeactivated()) left.copy(status = MatchStatus.CLOSED, deletedAt = now) else left
}
```

`MatchedTeams`:

```kotlin
/** [teamId]를 제외한 상대 팀이 모두 비활성(DEACTIVE)인지 여부. */
fun isLastActiveTeam(teamId: Long): Boolean =
    values.filter { it.teamId != teamId }.all { it.status == MatchedTeamStatus.DEACTIVE }

/** 모든 참가 팀이 비활성(DEACTIVE)인지 여부. */
fun allDeactivated(): Boolean =
    values.all { it.status == MatchedTeamStatus.DEACTIVE }

/** [teamId] 팀만 비활성 + soft delete 한 새 컬렉션. (나머지는 그대로) */
fun leave(teamId: Long, now: LocalDateTime): MatchedTeams =
    MatchedTeams(values.map { if (it.teamId == teamId) it.leave(now) else it })
```

`MatchedTeam`:

```kotlin
/** 이 팀을 비활성(DEACTIVE) + soft delete 한 새 모델. (성사된 매칭을 이 팀이 종료할 때) */
fun leave(now: LocalDateTime): MatchedTeam =
    copy(status = MatchedTeamStatus.DEACTIVE, deletedAt = now)
```

> 설계 메모 1: `MatchedTeamStatus.DEACTIVE`(현재 "팀 해체"용)를 매치별 상태로 재사용한다. `matched_team`은 매치별 상태를 보관하므로 "이 팀이 이 매치에서 빠짐" 의미로 자연스럽다. 새 상태값은 만들지 않는다. 기존 `MatchedTeam.deactivate()`(status만 변경)는 `close()` 경로(미성사 종료, 기록 보존)에서 계속 쓰이므로 그대로 두고, soft delete가 필요한 종료 경로용 `leave(now)`를 별도로 추가한다.
>
> 설계 메모 2 (soft delete on leave의 결과): `TeamMatchAdapter.findById`는 `matched_teams`를 `@SQLRestriction("deleted_at is null")` 엔티티로 로드하므로, **한 팀이 나간(soft delete된) 뒤 그 팀 매칭을 다시 조회하면 나간 팀 행이 빠진 채(상대 팀만) 로드된다.** 모든 정상 흐름은 이 상태에서도 올바르게 동작한다(마지막 팀이 나갈 때 남은 한 팀만 로드돼도 `allDeactivated()=true`로 헤더 CLOSED 판정, 알림 미발송이 성립). 부수 효과로 **이미 나간 팀이 종료를 다시 호출하면 그 팀이 참가 팀 목록에서 빠져 로드되어 `findByActiveMember`가 null → `NOT_TEAM_MATCH_PARTICIPANT`(403)** 가 된다(1:1의 409와 다른 지점). 그래서 `validateTerminable`에는 "이미 나간 팀" 검사를 넣지 않는다(도달 불가능). 또한 즉시 soft delete라 `findActiveByTeamId`에서 나간 팀이 그 매칭을 더 이상 진행 중으로 보지 않는다.

### 5. ErrorCode — `oneulsogae-core`

`TeamMatchErrorCode`에 추가 (1:1 `MatchErrorCode.MATCH_NOT_MATCHED` 미러):

```kotlin
TEAM_MATCH_NOT_MATCHED("TEAM-MATCH-004", "성사된 팀 매칭만 종료할 수 있습니다.", HttpStatus.CONFLICT),
```

(코드 번호는 기존 마지막 값 다음으로. 현재 `TEAM_MATCH_ALREADY_CLOSED`가 `TEAM-MATCH-003`이므로 `-004`.)

### 6. 도메인 이벤트 + 핸들러 + 알림 타입

이벤트 `com.org.oneulsogae.core.match.command.domain.event.TeamMatchEnded`:

```kotlin
/**
 * 성사된 팀 매칭을 한 팀이 종료(나감)했을 때 발행되는 도메인 이벤트.
 * [recipientUserIds]는 방에 남는 상대 팀의 활성 구성원, [fromTeamId]는 나간 팀(수신자 기준 상대 팀)이다.
 */
data class TeamMatchEnded(
    val teamMatchId: Long,
    val fromTeamId: Long,
    val recipientUserIds: List<Long>,
)
```

`TeamMatchEventHandler`에 핸들러 추가 (팀 단위 알림 — 닉네임 비노출, 기존 컨벤션):

```kotlin
/** 팀 매칭 종료(한 팀이 나감) → 방에 남는 상대 팀 구성원들에게 "매칭 종료" 알림. */
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW)
fun onTeamMatchEnded(event: TeamMatchEnded) {
    event.recipientUserIds.forEach { recipientUserId: Long ->
        saveAlarmUseCase.save(
            SaveAlarmCommand(
                userId = recipientUserId,
                type = AlarmType.MANY_TO_MANY_MATCH_ENDED,
                title = "매칭 종료",
                description = "상대 팀이 매칭을 종료했어요.",
                link = "/",
                fromTeamId = event.fromTeamId,
            ),
        )
    }
}
```

`AlarmType`에 추가:

```kotlin
/** [다대다 매칭] 성사된 매칭을 상대 팀이 종료(나감). (방에 남는 상대 팀에게) */
MANY_TO_MANY_MATCH_ENDED("매칭 종료"),
```

### 7. 채팅방 (수정 없음)

`DeactivateChatRoomMemberUseCase.deactivate(ChatRoomMatchType.TEAM, teamMatchId, userIds)`를 그대로 호출한다.
기존 `DeactivateChatRoomMemberService`가 TEAM 분기 안내 문구(`"상대 팀이 채팅방을 나갔어요"`)와 "활성 멤버 없으면 방·멤버 모두 종료" 처리를 이미 담당하므로 추가 변경이 없다.

## 테스트

### 유닛 (Kotest, `oneulsogae-core`)

- `TeamMatch.validateTerminable`: 비참가 팀(`NOT_TEAM_MATCH_PARTICIPANT`), CLOSED(`TEAM_MATCH_ALREADY_CLOSED`), 미성사(`TEAM_MATCH_NOT_MATCHED`), 정상(예외 없음).
- `TeamMatch.leave`: 상대 팀 활성 → 내 팀만 DEACTIVE+deletedAt, 헤더는 MATCHED 유지. 상대 팀 이미 DEACTIVE → 헤더까지 CLOSED+deletedAt.
- `TeamMatch.isLastActiveTeam`: 상대 활성 → false, 상대 DEACTIVE → true.
- `MatchedTeams` 신규 메서드(`isLastActiveTeam`/`allDeactivated`/`leave`).

### E2E (`oneulsogae-api`, `AbstractIntegrationSupport` + 픽스처)

- 정상 종료: 내 팀 `matched_team` 비활성·soft delete(조회에서 제외)·우리 팀원 채팅 멤버 비활성·상대 팀 유지·상대 팀 두 명에 알림 생성(`MANY_TO_MANY_MATCH_ENDED`, `fromTeamId`=나간 팀, `link`=`/`).
- 비참가자 → 403(`NOT_TEAM_MATCH_PARTICIPANT`).
- 미성사(PROPOSED/PARTIALLY_ACCEPTED) 종료 시도 → 409(`TEAM_MATCH_NOT_MATCHED`).
- 상대 팀이 이미 나간 뒤 마지막 종료 → `team_matches` 헤더 CLOSED + soft delete, 채팅방도 종료, 알림 미발송.
- 이미 나간 팀이 재호출 → 403(`NOT_TEAM_MATCH_PARTICIPANT`). (soft delete로 참가 팀 목록에서 빠지므로)

## 프론트엔드 영향 (백엔드는 수정하지 않음 — 안내만)

- 신규 `DELETE /team-matches/v1/{teamMatchId}` 호출 추가(미팅/채팅 화면의 "매칭 종료" 액션).
- 신규 알림 타입 `MANY_TO_MANY_MATCH_ENDED` 표시 처리(문구/아이콘/`/` 라우팅).

## 비목표

- 상대 팀의 `matched_team`이나 `team_matches` 헤더를 한 팀의 종료만으로 강제 종료하지 않는다(마지막 종료 제외).
- 종료 시 코인 환불은 하지 않는다(1:1 종료도 환불하지 않음).
- `MatchedTeamStatus`에 새 상태값을 추가하지 않는다.
