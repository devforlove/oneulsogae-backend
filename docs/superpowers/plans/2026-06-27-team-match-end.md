# 팀 매칭 종료 API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 성사(MATCHED)된 2:2 팀 매칭을 한 팀이 종료하는 API(`DELETE /team-matches/v1/{teamMatchId}`)를 추가한다.

**Architecture:** 1:1 매칭 종료(`EndMatchService`)를 미러하되 참가 단위가 "팀"이다. 헥사고날: Controller→in-port(`EndTeamMatchUseCase`)→Service(`EndTeamMatchService`)→도메인(`TeamMatch.leave`)·out-port. 나간 팀의 `matched_team`만 DEACTIVE+soft delete, 상대 팀도 이미 나간 마지막 종료면 `team_matches` 헤더까지 CLOSED+soft delete. 채팅 비활성·알림은 기존 in-port/이벤트 패턴 재사용.

**Tech Stack:** Kotlin 2.2.21, Spring Boot 4, Spring Data JPA, Kotest(DescribeSpec) 유닛 + Testcontainers E2E.

## Global Constraints

- 응답/주석/메세지는 한국어. `oneulsogae-backend`만 수정(프론트는 안내만).
- 타입 명시(변수·반환·람다 파라미터), `LocalDateTime.now()` 직접 호출 금지(`TimeGenerator.now()` 주입), 도메인 규칙은 도메인 모델에 캡슐화.
- 명령 경로: out-port·도메인은 `command` 패키지. 다른 도메인은 in-port로만 참조.
- 도메인 모델은 불변(data class `copy`로 새 인스턴스 반환).
- 도메인 유닛 테스트는 `oneulsogae-api` 모듈의 `src/test/.../domain/match`에 위치(기존 `TeamMatchTest`/`MatchedTeamsTest`와 동일 위치).
- 빌드/테스트: `./gradlew`.

---

### Task 1: 도메인 — MatchedTeam.leave + MatchedTeams 헬퍼 + 에러코드

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/MatchedTeam.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/MatchedTeams.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/TeamMatchErrorCode.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/match/MatchedTeamsTest.kt`

**Interfaces:**
- Produces:
  - `MatchedTeam.leave(now: LocalDateTime): MatchedTeam` — status=DEACTIVE, deletedAt=now.
  - `MatchedTeams.leave(teamId: Long, now: LocalDateTime): MatchedTeams` — 해당 teamId만 leave.
  - `MatchedTeams.isLastActiveTeam(teamId: Long): Boolean` — teamId 제외 나머지 모두 DEACTIVE.
  - `MatchedTeams.allDeactivated(): Boolean` — 전원 DEACTIVE.
  - `TeamMatchErrorCode.TEAM_MATCH_NOT_MATCHED` (`TEAM-MATCH-004`, 409).

- [ ] **Step 1: 실패하는 테스트 작성** — `MatchedTeamsTest.kt`의 마지막 `describe` 뒤(닫는 `})` 직전)에 추가:

```kotlin
	describe("leave - 특정 팀 종료(비활성+soft delete)") {
		it("주어진 teamId 팀만 DEACTIVE + deletedAt을 채우고 나머지는 그대로다") {
			val now: LocalDateTime = LocalDateTime.of(2026, 6, 27, 12, 0)
			val matchedTeams: MatchedTeams = MatchedTeams.of(listOf(10L, 20L)).activateAll()

			val left: MatchedTeams = matchedTeams.leave(10L, now)

			val ten: MatchedTeam = left.values.first { it.teamId == 10L }
			ten.status shouldBe MatchedTeamStatus.DEACTIVE
			ten.deletedAt shouldBe now
			val twenty: MatchedTeam = left.values.first { it.teamId == 20L }
			twenty.status shouldBe MatchedTeamStatus.ACTIVE
			twenty.deletedAt shouldBe null
		}
	}

	describe("isLastActiveTeam / allDeactivated") {
		it("상대 팀이 활성이면 isLastActiveTeam=false, allDeactivated=false") {
			val matchedTeams: MatchedTeams = MatchedTeams.of(listOf(10L, 20L)).activateAll()

			matchedTeams.isLastActiveTeam(10L) shouldBe false
			matchedTeams.allDeactivated() shouldBe false
		}

		it("상대 팀이 이미 DEACTIVE면 isLastActiveTeam=true, 내가 나가면 allDeactivated=true") {
			val now: LocalDateTime = LocalDateTime.of(2026, 6, 27, 12, 0)
			val onlyTenActive: MatchedTeams = MatchedTeams.of(listOf(10L, 20L)).activateAll().leave(20L, now)

			onlyTenActive.isLastActiveTeam(10L) shouldBe true
			onlyTenActive.allDeactivated() shouldBe false
			onlyTenActive.leave(10L, now).allDeactivated() shouldBe true
		}
	}
```

그리고 같은 파일 import에 다음을 추가:

```kotlin
import com.org.oneulsogae.core.match.command.domain.MatchedTeam
import java.time.LocalDateTime
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.match.MatchedTeamsTest"`
Expected: 컴파일 에러/FAIL (`leave`/`isLastActiveTeam`/`allDeactivated` 미정의)

- [ ] **Step 3: 도메인 구현** — `MatchedTeam.kt`의 `deactivate()` 아래에 추가:

```kotlin
	/** 이 팀을 비활성(DEACTIVE) + soft delete([deletedAt])한 새 모델을 반환한다. (성사된 매칭을 이 팀이 종료할 때) */
	fun leave(now: LocalDateTime): MatchedTeam =
		copy(status = MatchedTeamStatus.DEACTIVE, deletedAt = now)
```

`MatchedTeam.kt` 상단 import에 추가(이미 있으면 생략): `import java.time.LocalDateTime`

`MatchedTeams.kt`의 `apply(teamId)` 아래에 추가:

```kotlin
	/** [teamId] 팀만 비활성 + soft delete한 새 컬렉션을 반환한다. (나머지는 그대로) */
	fun leave(teamId: Long, now: LocalDateTime): MatchedTeams =
		MatchedTeams(values.map { matchedTeam: MatchedTeam -> if (matchedTeam.teamId == teamId) matchedTeam.leave(now) else matchedTeam })

	/** [teamId]를 제외한 상대 팀이 모두 비활성(DEACTIVE)인지 여부. (이 팀이 나가면 알릴 상대가 없는 마지막 종료) */
	fun isLastActiveTeam(teamId: Long): Boolean =
		values.filter { matchedTeam: MatchedTeam -> matchedTeam.teamId != teamId }
			.all { matchedTeam: MatchedTeam -> matchedTeam.status == MatchedTeamStatus.DEACTIVE }

	/** 모든 참가 팀이 비활성(DEACTIVE)인지 여부. */
	fun allDeactivated(): Boolean =
		values.all { matchedTeam: MatchedTeam -> matchedTeam.status == MatchedTeamStatus.DEACTIVE }
```

`MatchedTeams.kt` 상단 import에 추가: `import com.org.oneulsogae.common.match.MatchedTeamStatus`, `import java.time.LocalDateTime`

`TeamMatchErrorCode.kt`의 `TEAM_MATCH_ALREADY_CLOSED` 줄 아래에 추가:

```kotlin
	TEAM_MATCH_NOT_MATCHED("TEAM-MATCH-004", "성사된 팀 매칭만 종료할 수 있습니다.", HttpStatus.CONFLICT),
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.match.MatchedTeamsTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/MatchedTeam.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/MatchedTeams.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/TeamMatchErrorCode.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/match/MatchedTeamsTest.kt
git commit -m "feat(match): MatchedTeam/MatchedTeams 팀 종료(leave) 도메인 + 에러코드 추가"
```

---

### Task 2: 도메인 — TeamMatch.validateTerminable / isLastActiveTeam / leave

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/TeamMatch.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/match/TeamMatchTest.kt`

**Interfaces:**
- Consumes: `MatchedTeams.leave/isLastActiveTeam/allDeactivated` (Task 1), `TeamMatchErrorCode.TEAM_MATCH_NOT_MATCHED` (Task 1).
- Produces:
  - `TeamMatch.validateTerminable(teamId: Long)`
  - `TeamMatch.isLastActiveTeam(teamId: Long): Boolean`
  - `TeamMatch.leave(teamId: Long, now: LocalDateTime): TeamMatch`

- [ ] **Step 1: 실패하는 테스트 작성** — `TeamMatchTest.kt`의 마지막 `describe("validateRespondable")` 블록 뒤(닫는 `})` 직전)에 추가. (헬퍼 `matched()`로 성사 상태를 만든다)

```kotlin
	describe("validateTerminable - 팀 매칭 종료 검증") {
		// 양 팀 신청으로 성사(MATCHED)된 팀 매칭
		fun matched(): TeamMatch =
			TeamMatch.propose(10L, 20L, TeamMatchType.RECOMMENDED, now).respond(10L).respond(20L)

		it("참가 팀이 아니면 NOT_TEAM_MATCH_PARTICIPANT를 던진다") {
			val ex: BusinessException = shouldThrow { matched().validateTerminable(99L) }
			ex.errorCode shouldBe TeamMatchErrorCode.NOT_TEAM_MATCH_PARTICIPANT
		}

		it("이미 종료(CLOSED)된 매칭이면 TEAM_MATCH_ALREADY_CLOSED를 던진다") {
			val closed: TeamMatch = matched().copy(status = MatchStatus.CLOSED)
			val ex: BusinessException = shouldThrow { closed.validateTerminable(10L) }
			ex.errorCode shouldBe TeamMatchErrorCode.TEAM_MATCH_ALREADY_CLOSED
		}

		it("성사(MATCHED) 전이면 TEAM_MATCH_NOT_MATCHED를 던진다") {
			val partiallyAccepted: TeamMatch = TeamMatch.propose(10L, 20L, TeamMatchType.RECOMMENDED, now).respond(10L)
			val ex: BusinessException = shouldThrow { partiallyAccepted.validateTerminable(10L) }
			ex.errorCode shouldBe TeamMatchErrorCode.TEAM_MATCH_NOT_MATCHED
		}

		it("성사된 매칭의 참가 팀이면 예외 없이 통과한다") {
			matched().validateTerminable(10L)
		}
	}

	describe("isLastActiveTeam") {
		fun matched(): TeamMatch =
			TeamMatch.propose(10L, 20L, TeamMatchType.RECOMMENDED, now).respond(10L).respond(20L)

		it("상대 팀이 활성이면 false다") {
			matched().isLastActiveTeam(10L) shouldBe false
		}
	}

	describe("leave - 팀 매칭 종료") {
		fun matched(): TeamMatch =
			TeamMatch.propose(10L, 20L, TeamMatchType.RECOMMENDED, now).respond(10L).respond(20L)

		it("상대 팀이 활성이면 내 팀만 DEACTIVE+deletedAt이 되고 헤더는 MATCHED로 유지된다") {
			val left: TeamMatch = matched().leave(10L, now)

			left.status shouldBe MatchStatus.MATCHED
			val ten: com.org.oneulsogae.core.match.command.domain.MatchedTeam = left.matchedTeams.values.first { it.teamId == 10L }
			ten.status shouldBe MatchedTeamStatus.DEACTIVE
			ten.deletedAt shouldBe now
			left.matchedTeams.values.first { it.teamId == 20L }.status shouldBe MatchedTeamStatus.ACTIVE
			left.deletedAt shouldBe null
		}

		it("상대 팀이 이미 나간 마지막 종료면 헤더까지 CLOSED+deletedAt이 된다") {
			val onlyTenActive: TeamMatch = matched().leave(20L, now)

			val closed: TeamMatch = onlyTenActive.leave(10L, now)

			closed.status shouldBe MatchStatus.CLOSED
			closed.deletedAt shouldBe now
			closed.matchedTeams.values.all { it.status == MatchedTeamStatus.DEACTIVE } shouldBe true
		}
	}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.match.TeamMatchTest"`
Expected: 컴파일 에러/FAIL (`validateTerminable`/`isLastActiveTeam`/`leave` 미정의)

- [ ] **Step 3: 도메인 구현** — `TeamMatch.kt`의 `validateRespondable(...)` 아래에 추가:

```kotlin
	/**
	 * [teamId] 팀이 이 팀 매칭을 종료할 수 있는 상태인지 검증한다.
	 * 참가 팀이 아니면 [TeamMatchErrorCode.NOT_TEAM_MATCH_PARTICIPANT], 이미 종료(CLOSED)면 [TeamMatchErrorCode.TEAM_MATCH_ALREADY_CLOSED],
	 * 아직 성사(MATCHED)되지 않았으면 [TeamMatchErrorCode.TEAM_MATCH_NOT_MATCHED]를 던진다. (성사된 매칭만 종료 가능)
	 */
	fun validateTerminable(teamId: Long) {
		if (!isParticipant(teamId)) {
			throw BusinessException(TeamMatchErrorCode.NOT_TEAM_MATCH_PARTICIPANT)
		}
		// MATCHED도 isClosed()=true(더 이상 응답을 안 받음)라, 여기선 종료(CLOSED)만 따로 거른다.
		if (status == MatchStatus.CLOSED) {
			throw BusinessException(TeamMatchErrorCode.TEAM_MATCH_ALREADY_CLOSED)
		}
		if (status != MatchStatus.MATCHED) {
			throw BusinessException(TeamMatchErrorCode.TEAM_MATCH_NOT_MATCHED)
		}
	}

	/** [teamId]의 상대 팀이 모두 비활성인지 여부. (이 팀이 나가면 방에 남아 알림을 받을 상대 팀이 없는 마지막 종료) */
	fun isLastActiveTeam(teamId: Long): Boolean =
		matchedTeams.isLastActiveTeam(teamId)

	/**
	 * [teamId] 팀이 이 매칭을 나간 새 모델을 반환한다.
	 * 내 팀 참가([MatchedTeam])만 비활성·소프트 삭제하되, 상대 팀도 모두 비활성이면(마지막 종료) 헤더까지 [MatchStatus.CLOSED]·소프트 삭제한다.
	 * (혼자 나가면 헤더는 MATCHED로 유지되고 상대 팀은 그대로 남는다)
	 */
	fun leave(teamId: Long, now: LocalDateTime): TeamMatch {
		val left: TeamMatch = copy(matchedTeams = matchedTeams.leave(teamId, now))
		return if (left.matchedTeams.allDeactivated()) left.copy(status = MatchStatus.CLOSED, deletedAt = now) else left
	}
```

(`TeamMatch.kt`는 이미 `MatchStatus`, `BusinessException`, `TeamMatchErrorCode`, `java.time.LocalDateTime`를 import하고 있어 추가 import 불필요)

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.match.TeamMatchTest"`
Expected: PASS

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/TeamMatch.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/match/TeamMatchTest.kt
git commit -m "feat(match): TeamMatch 종료(validateTerminable/leave/isLastActiveTeam) 도메인 추가"
```

---

### Task 3: 알림 — AlarmType + TeamMatchEnded 이벤트 + 핸들러

**Files:**
- Modify: `oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/alarm/AlarmType.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/event/TeamMatchEnded.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/TeamMatchEventHandler.kt`

**Interfaces:**
- Produces:
  - `AlarmType.MANY_TO_MANY_MATCH_ENDED`
  - `TeamMatchEnded(teamMatchId: Long, fromTeamId: Long, recipientUserIds: List<Long>)`
  - `TeamMatchEventHandler.onTeamMatchEnded(event: TeamMatchEnded)`

이 Task는 이벤트/핸들러가 한 단위로 묶여야 컴파일·검증되므로 함께 구현한다. 핸들러는 기존 `onTeamMatchInterestSent`/`onTeamMatchAccepted`와 동일 패턴이라 E2E(Task 5)에서 알림 생성으로 검증한다(여기서는 컴파일만).

- [ ] **Step 1: AlarmType 추가** — `AlarmType.kt`의 `MANY_TO_MANY_MATCHED` 줄 아래에 추가:

```kotlin
	/** [다대다 매칭] 성사된 매칭을 상대 팀이 종료(나감). (방에 남는 상대 팀에게) */
	MANY_TO_MANY_MATCH_ENDED("매칭 종료"),
```

- [ ] **Step 2: 이벤트 생성** — 새 파일 `TeamMatchEnded.kt`:

```kotlin
package com.org.oneulsogae.core.match.command.domain.event

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

- [ ] **Step 3: 핸들러 추가** — `TeamMatchEventHandler.kt`의 `onTeamMatchAccepted(...)` 아래(클래스 닫는 `}` 직전)에 추가:

```kotlin
	/** 팀 매칭 종료(한 팀이 나감) → 방에 남는 상대 팀 구성원들에게 "매칭 종료" 알림. (마지막 종료면 이벤트가 발행되지 않아 호출되지 않는다) */
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

`TeamMatchEventHandler.kt` 상단 import에 추가:

```kotlin
import com.org.oneulsogae.core.match.command.domain.event.TeamMatchEnded
```

- [ ] **Step 4: 컴파일 확인**

Run: `./gradlew :oneulsogae-core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/alarm/AlarmType.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/event/TeamMatchEnded.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/TeamMatchEventHandler.kt
git commit -m "feat(alarm): 팀 매칭 종료 알림(MANY_TO_MANY_MATCH_ENDED) 이벤트/핸들러 추가"
```

---

### Task 4: in-port + Service (EndTeamMatchService)

**Files:**
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/port/in/EndTeamMatchUseCase.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/EndTeamMatchService.kt`

**Interfaces:**
- Consumes: `TeamMatch.validateTerminable/isLastActiveTeam/leave` (Task 2), `TeamMatchEnded` (Task 3), 기존 `GetTeamMatchPort.findById`, `SaveTeamMatchPort.save`, `GetTeamPort.findById`, `Teams.findByActiveMember/opponentActiveMemberIds`, `Team.id/activeMemberIds`, `DeactivateChatRoomMemberUseCase.deactivate`, `DomainEventPublisher.publish`, `TimeGenerator.now`.
- Produces: `EndTeamMatchUseCase.endTeamMatch(userId: Long, teamMatchId: Long)`.

Service는 외부 협력자(채팅/알림/분산 락/DB)가 많아 유닛 테스트보다 E2E(Task 5)로 검증하는 것이 적절하다(기존 `EndMatchService`도 E2E로 검증). 이 Task는 작성+컴파일까지.

- [ ] **Step 1: in-port 생성** — `EndTeamMatchUseCase.kt`:

```kotlin
package com.org.oneulsogae.core.match.command.application.port.`in`

/**
 * 팀 매칭 종료 인포트(유스케이스).
 * 성사(MATCHED)된 팀 매칭에서 [userId]가 속한 팀이 매칭을 종료한다.
 * 참가·성사 검증 → 내 팀 비활성/soft delete + 우리 팀원 채팅 비활성 + 상대 팀 알림.
 */
interface EndTeamMatchUseCase {

	fun endTeamMatch(userId: Long, teamMatchId: Long)
}
```

- [ ] **Step 2: Service 생성** — `EndTeamMatchService.kt`:

```kotlin
package com.org.oneulsogae.core.match.command.application

import com.org.oneulsogae.common.chat.ChatRoomMatchType
import com.org.oneulsogae.core.chat.command.application.port.`in`.DeactivateChatRoomMemberUseCase
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.common.event.DomainEventPublisher
import com.org.oneulsogae.core.common.lock.DistributedLock
import com.org.oneulsogae.core.common.lock.LockKeyConstraints
import com.org.oneulsogae.core.common.time.TimeGenerator
import com.org.oneulsogae.core.match.TeamMatchErrorCode
import com.org.oneulsogae.core.match.command.application.port.`in`.EndTeamMatchUseCase
import com.org.oneulsogae.core.match.command.application.port.out.GetTeamMatchPort
import com.org.oneulsogae.core.match.command.application.port.out.GetTeamPort
import com.org.oneulsogae.core.match.command.application.port.out.SaveTeamMatchPort
import com.org.oneulsogae.core.match.command.domain.Team
import com.org.oneulsogae.core.match.command.domain.TeamMatch
import com.org.oneulsogae.core.match.command.domain.Teams
import com.org.oneulsogae.core.match.command.domain.event.TeamMatchEnded
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [EndTeamMatchUseCase] 구현. 성사된 팀 매칭을 한 팀이 종료한다. (1:1 [EndMatchService] 미러)
 * 팀 매칭·참가 두 팀을 로드해 행위자가 속한 ACTIVE 팀을 식별하고, 종료 가능 상태를 검증한 뒤 처리한다:
 * 내 팀 참가([com.org.oneulsogae.core.match.command.domain.MatchedTeam])만 비활성·소프트 삭제하고(상대도 모두 나갔으면 헤더까지 CLOSED·소프트 삭제),
 * 우리 팀원 전원을 채팅방에서 비활성화하며(남는 상대 팀엔 나감 안내), 방에 남는 상대 팀이 있으면 종료 알림을 발행한다.
 *
 * 상태 변경·채팅 처리는 같은 트랜잭션이라 한 단계라도 실패하면 함께 롤백된다. 알림만 커밋 후 best-effort([TeamMatchEventHandler])다.
 * 다른 도메인(chat)은 자기 out-port가 아니라 in-port로 참조한다.
 * 팀 매칭별 분산 락([DistributedLock], "TEAM_MATCH_INTEREST::{teamMatchId}")으로 신청/수락과의 경합을 막는다. (waitTime=0, 겹치면 즉시 409)
 */
@Service
class EndTeamMatchService(
	private val getTeamMatchPort: GetTeamMatchPort,
	private val saveTeamMatchPort: SaveTeamMatchPort,
	private val getTeamPort: GetTeamPort,
	private val deactivateChatRoomMemberUseCase: DeactivateChatRoomMemberUseCase,
	private val domainEventPublisher: DomainEventPublisher,
	private val timeGenerator: TimeGenerator,
) : EndTeamMatchUseCase {

	@DistributedLock(prefix = LockKeyConstraints.TEAM_MATCH_INTEREST, keys = ["#teamMatchId"], waitTime = 0)
	@Transactional
	override fun endTeamMatch(userId: Long, teamMatchId: Long) {
		val teamMatch: TeamMatch = getTeamMatchPort.findById(teamMatchId)
			?: throw BusinessException(TeamMatchErrorCode.TEAM_MATCH_NOT_FOUND)

		// 참가 두 팀을 로드해 행위자가 ACTIVE 구성원으로 속한 팀을 식별한다. (참가 검증 겸함)
		val teams: Teams = Teams(teamMatch.matchedTeams.teamIds().mapNotNull { teamId: Long -> getTeamPort.findById(teamId) })
		val actorTeam: Team = teams.findByActiveMember(userId)
			?: throw BusinessException(TeamMatchErrorCode.NOT_TEAM_MATCH_PARTICIPANT)

		teamMatch.validateTerminable(actorTeam.id)

		val now: LocalDateTime = timeGenerator.now()
		// 상대 팀이 이미 나갔는지(= 알릴 상대가 없는 마지막 종료인지)를 leave 전에 판단한다.
		val isLastTeam: Boolean = teamMatch.isLastActiveTeam(actorTeam.id)

		saveTeamMatchPort.save(teamMatch.leave(actorTeam.id, now))

		// 우리 팀원 전원을 채팅방에서 비활성화한다. (남는 상대 팀에 "상대 팀이 채팅방을 나갔어요" 안내 — 기존 TEAM 분기 재사용)
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
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :oneulsogae-core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/port/in/EndTeamMatchUseCase.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/EndTeamMatchService.kt
git commit -m "feat(match): 팀 매칭 종료 유스케이스/서비스(EndTeamMatchService) 추가"
```

---

### Task 5: 컨트롤러 엔드포인트 + E2E 테스트

**Files:**
- Modify: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/match/TeamMatchController.kt`
- Create: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/EndTeamMatchE2ETest.kt`

**Interfaces:**
- Consumes: `EndTeamMatchUseCase.endTeamMatch` (Task 4).

- [ ] **Step 1: 컨트롤러에 엔드포인트 추가** — `TeamMatchController.kt`:

생성자에 `EndTeamMatchUseCase` 주입 추가:

```kotlin
	private val sendTeamInterestUseCase: SendTeamInterestUseCase,
	private val endTeamMatchUseCase: EndTeamMatchUseCase,
	private val getMeetingTabUseCase: GetMeetingTabUseCase,
	private val timeGenerator: TimeGenerator,
```

`sendInterest` 메서드 아래에 엔드포인트 추가:

```kotlin
	@Operation(
		summary = "팀 매칭 종료",
		description = "성사된 팀 매칭을 종료한다. 종료한 팀의 참가만 비활성화하고, 우리 팀원 전원을 채팅방에서 내보낸 뒤 방에 남는 상대 팀에 종료 알림을 보낸다.",
	)
	@DeleteMapping("/{teamMatchId}")
	fun endTeamMatch(
		@LoginUser user: AuthUser,
		@PathVariable teamMatchId: Long,
	): ApiResponse<Unit> {
		endTeamMatchUseCase.endTeamMatch(user.id, teamMatchId)
		return ApiResponse.success()
	}
```

import 추가:

```kotlin
import com.org.oneulsogae.core.match.command.application.port.`in`.EndTeamMatchUseCase
import org.springframework.web.bind.annotation.DeleteMapping
```

- [ ] **Step 2: E2E 테스트 작성** — `EndTeamMatchE2ETest.kt`:

```kotlin
package com.org.oneulsogae.api.match

import com.org.oneulsogae.common.alarm.AlarmType
import com.org.oneulsogae.common.chat.ChatMessageType
import com.org.oneulsogae.common.chat.ChatRoomMatchType
import com.org.oneulsogae.common.chat.ChatRoomMemberStatus
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.delete
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.match.MatchStatus
import com.org.oneulsogae.common.match.MatchedTeamStatus
import com.org.oneulsogae.common.match.TeamMatchType
import com.org.oneulsogae.common.match.TeamMemberStatus
import com.org.oneulsogae.common.match.TeamStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.alarm.command.entity.AlarmEntity
import com.org.oneulsogae.infra.alarm.command.entity.QAlarmEntity
import com.org.oneulsogae.infra.chat.command.entity.ChatMessageEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatMessageEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomMemberEntity
import com.org.oneulsogae.infra.fixture.ChatRoomEntityFixture
import com.org.oneulsogae.infra.fixture.ChatRoomMemberEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.match.command.entity.MatchedTeamEntity
import com.org.oneulsogae.infra.match.command.entity.QMatchedTeamEntity
import com.org.oneulsogae.infra.match.command.entity.QTeamEntity
import com.org.oneulsogae.infra.match.command.entity.QTeamMatchEntity
import com.org.oneulsogae.infra.match.command.entity.QTeamMemberEntity
import com.org.oneulsogae.infra.match.command.entity.TeamEntity
import com.org.oneulsogae.infra.match.command.entity.TeamMatchEntity
import com.org.oneulsogae.infra.match.command.entity.TeamMemberEntity
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * `DELETE /team-matches/v1/{teamMatchId}` E2E 테스트. (팀 매칭 종료 엔드포인트)
 *
 * 성사(MATCHED)된 팀 매칭을 한 팀이 종료하면, 그 팀의 matched_team만 DEACTIVE+소프트 삭제되고(상대 팀·헤더 유지),
 * 우리 팀원 전원이 채팅방에서 DEACTIVE가 되며 남는 상대 팀에 "상대 팀이 채팅방을 나갔어요" 메세지와 "매칭 종료" 알림이 간다.
 * 상대 팀까지 모두 나간 마지막 종료에서는 team_matches 헤더가 CLOSED+소프트 삭제되고 알림은 가지 않는다.
 * 실제 서버(RANDOM_PORT) + Testcontainers(MySQL/Redis, 분산 락 포함)를 기동하고 HTTP를 호출한다.
 */
class EndTeamMatchE2ETest : AbstractIntegrationSupport({

	// 같은 성별 두 명으로 ACTIVE 팀을 만들고 teamId를 돌려준다.
	fun persistTeam(gender: Gender, vararg memberUserIds: Long): Long {
		val team: TeamEntity = IntegrationUtil.persist(
			TeamEntity(name = "팀", gender = gender, regionId = 1L, introduction = "소개", status = TeamStatus.ACTIVE),
		)
		val teamId: Long = team.id!!
		memberUserIds.forEach { uid: Long ->
			IntegrationUtil.persist(TeamMemberEntity(teamId = teamId, userId = uid, status = TeamMemberStatus.ACTIVE))
		}
		return teamId
	}

	// 두 팀을 MATCHED 팀 매칭(matched_teams 둘 다 ACTIVE)으로 묶고 teamMatchId를 돌려준다.
	fun persistMatchedTeamMatch(teamAId: Long, teamBId: Long): Long {
		val teamMatch: TeamMatchEntity = IntegrationUtil.persist(
			TeamMatchEntity(
				memberKey = listOf(teamAId, teamBId).sorted().joinToString("-"),
				introducedDate = LocalDate.of(2026, 6, 27),
				expiresAt = LocalDateTime.of(2126, 6, 27, 0, 0),
				status = MatchStatus.MATCHED,
				matchType = TeamMatchType.RECOMMENDED,
				dateInitAmount = 40,
				dateAcceptAmount = 40,
			),
		)
		val teamMatchId: Long = teamMatch.id!!
		IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = teamMatchId, teamId = teamAId, status = MatchedTeamStatus.ACTIVE))
		IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = teamMatchId, teamId = teamBId, status = MatchedTeamStatus.ACTIVE))
		return teamMatchId
	}

	// 팀 매칭에 연결된 4인 ACTIVE 채팅방을 만들고 채팅방 id를 돌려준다.
	fun persistChatRoom(teamMatchId: Long, teamAId: Long, teamAUsers: List<Long>, teamBId: Long, teamBUsers: List<Long>): Long {
		val room = IntegrationUtil.persist(ChatRoomEntityFixture.create(matchType = ChatRoomMatchType.TEAM, matchId = teamMatchId))
		val roomId: Long = room.id!!
		teamAUsers.forEach { uid: Long -> IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = uid, teamId = teamAId)) }
		teamBUsers.forEach { uid: Long -> IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = uid, teamId = teamBId)) }
		return roomId
	}

	describe("DELETE /team-matches/v1/{teamMatchId}") {

		context("성사된 팀 매칭을 한 팀이 종료하면") {
			it("내 팀 matched_team만 비활성·소프트 삭제되고, 우리 팀원이 채팅방에서 나가며 상대 팀에 안내·알림이 간다 (200)") {
				val a1 = 4101L
				val a2 = 4102L
				val b1 = 5101L
				val b2 = 5102L
				val teamA: Long = persistTeam(Gender.MALE, a1, a2)
				val teamB: Long = persistTeam(Gender.FEMALE, b1, b2)
				val teamMatchId: Long = persistMatchedTeamMatch(teamA, teamB)
				val roomId: Long = persistChatRoom(teamMatchId, teamA, listOf(a1, a2), teamB, listOf(b1, b2))

				delete("/team-matches/v1/$teamMatchId") {
					bearer(accessTokenFor(a1))
				} expect {
					status(200)
					body("success", true)
				}

				// 내 팀(A) matched_team은 소프트 삭제로 조회에서 제외, 상대 팀(B)은 ACTIVE 유지, 헤더는 MATCHED 유지
				matchedTeamStatus(teamMatchId, teamA) shouldBe null
				matchedTeamStatus(teamMatchId, teamB) shouldBe MatchedTeamStatus.ACTIVE
				teamMatchStatus(teamMatchId) shouldBe MatchStatus.MATCHED
				// 우리 팀원 전원(A) 채팅 DEACTIVE, 상대 팀(B) ACTIVE 유지
				memberStatus(roomId, a1) shouldBe ChatRoomMemberStatus.DEACTIVE
				memberStatus(roomId, a2) shouldBe ChatRoomMemberStatus.DEACTIVE
				memberStatus(roomId, b1) shouldBe ChatRoomMemberStatus.ACTIVE
				memberStatus(roomId, b2) shouldBe ChatRoomMemberStatus.ACTIVE
				// 방에 "상대 팀이 채팅방을 나갔어요" 시스템 메세지가 남는다
				val systemMessages: List<ChatMessageEntity> = chatMessages(roomId).filter { it.type == ChatMessageType.SYSTEM }
				systemMessages.size shouldBe 1
				systemMessages.first().content shouldBe "상대 팀이 채팅방을 나갔어요"
				// 상대 팀 두 명에게 "매칭 종료" 알림(fromTeamId=나간 팀 A), 우리 팀엔 알림 없음
				alarmsOf(b1).map { it.type } shouldBe listOf(AlarmType.MANY_TO_MANY_MATCH_ENDED)
				alarmsOf(b1).first().fromTeamId shouldBe teamA
				alarmsOf(b1).first().description shouldBe "상대 팀이 매칭을 종료했어요."
				alarmsOf(b2).size shouldBe 1
				alarmsOf(a1).size shouldBe 0
			}
		}

		context("상대 팀이 이미 나간 뒤 마지막 팀이 종료하면") {
			it("team_matches 헤더가 CLOSED·소프트 삭제되고, 채팅방도 종료되며 알림이 가지 않는다 (200)") {
				val a1 = 4201L
				val a2 = 4202L
				val b1 = 5201L
				val b2 = 5202L
				val teamA: Long = persistTeam(Gender.MALE, a1, a2)
				val teamB: Long = persistTeam(Gender.FEMALE, b1, b2)
				val teamMatchId: Long = persistMatchedTeamMatch(teamA, teamB)
				val roomId: Long = persistChatRoom(teamMatchId, teamA, listOf(a1, a2), teamB, listOf(b1, b2))

				// 1차: 팀 A 종료 (헤더 유지, 상대 팀 B에 알림)
				delete("/team-matches/v1/$teamMatchId") { bearer(accessTokenFor(a1)) } expect { status(200) }
				teamMatchExists(teamMatchId) shouldBe true
				alarmsOf(b1).size shouldBe 1

				// 2차: 마지막 팀 B 종료 → 헤더 CLOSED·소프트 삭제, 채팅방 종료, 팀 A에 알림 없음
				delete("/team-matches/v1/$teamMatchId") { bearer(accessTokenFor(b1)) } expect { status(200) }
				teamMatchExists(teamMatchId) shouldBe false
				roomExists(roomId) shouldBe false
				alarmsOf(a1).size shouldBe 0
			}
		}

		context("아직 성사되지 않은(PARTIALLY_ACCEPTED) 팀 매칭을 종료하려 하면") {
			it("409(TEAM-MATCH-004)를 반환하고 매칭은 그대로다") {
				val a1 = 4301L
				val a2 = 4302L
				val b1 = 5301L
				val b2 = 5302L
				val teamA: Long = persistTeam(Gender.MALE, a1, a2)
				val teamB: Long = persistTeam(Gender.FEMALE, b1, b2)
				val teamMatch: TeamMatchEntity = IntegrationUtil.persist(
					TeamMatchEntity(
						memberKey = listOf(teamA, teamB).sorted().joinToString("-"),
						introducedDate = LocalDate.of(2026, 6, 27),
						expiresAt = LocalDateTime.of(2026, 6, 28, 0, 0),
						status = MatchStatus.PARTIALLY_ACCEPTED,
						matchType = TeamMatchType.RECOMMENDED,
						dateInitAmount = 40,
						dateAcceptAmount = 40,
					),
				)
				val teamMatchId: Long = teamMatch.id!!
				IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = teamMatchId, teamId = teamA, status = MatchedTeamStatus.APPLY))
				IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = teamMatchId, teamId = teamB, status = MatchedTeamStatus.WAITING))

				delete("/team-matches/v1/$teamMatchId") {
					bearer(accessTokenFor(a1))
				} expect {
					status(409)
					body("success", false)
					body("error.code", "TEAM-MATCH-004")
				}

				teamMatchStatus(teamMatchId) shouldBe MatchStatus.PARTIALLY_ACCEPTED
			}
		}

		context("참가 팀 구성원이 아닌 사용자가 종료하려 하면") {
			it("403(TEAM-MATCH-002)을 반환한다") {
				val a1 = 4401L
				val a2 = 4402L
				val b1 = 5401L
				val b2 = 5402L
				val strangerId = 9401L
				val teamA: Long = persistTeam(Gender.MALE, a1, a2)
				val teamB: Long = persistTeam(Gender.FEMALE, b1, b2)
				val teamMatchId: Long = persistMatchedTeamMatch(teamA, teamB)

				delete("/team-matches/v1/$teamMatchId") {
					bearer(accessTokenFor(strangerId))
				} expect {
					status(403)
					body("success", false)
					body("error.code", "TEAM-MATCH-002")
				}

				teamMatchStatus(teamMatchId) shouldBe MatchStatus.MATCHED
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				delete("/team-matches/v1/1") {} expect {
					status(401)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QAlarmEntity.alarmEntity)
		IntegrationUtil.deleteAll(QChatMessageEntity.chatMessageEntity)
		IntegrationUtil.deleteAll(QChatRoomMemberEntity.chatRoomMemberEntity)
		IntegrationUtil.deleteAll(QChatRoomEntity.chatRoomEntity)
		IntegrationUtil.deleteAll(QMatchedTeamEntity.matchedTeamEntity)
		IntegrationUtil.deleteAll(QTeamMatchEntity.teamMatchEntity)
		IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
		IntegrationUtil.deleteAll(QTeamEntity.teamEntity)
	}
})

// @SQLRestriction("deleted_at is null") 적용 — 소프트 삭제된 matched_team은 조회에서 빠지므로 null이면 종료된 것.
private fun matchedTeamStatus(teamMatchId: Long, teamId: Long): MatchedTeamStatus? {
	val q: QMatchedTeamEntity = QMatchedTeamEntity.matchedTeamEntity
	return IntegrationUtil.getQuery().select(q.status).from(q)
		.where(q.teamMatchId.eq(teamMatchId).and(q.teamId.eq(teamId))).fetchOne()
}

private fun teamMatchStatus(teamMatchId: Long): MatchStatus {
	val q: QTeamMatchEntity = QTeamMatchEntity.teamMatchEntity
	return IntegrationUtil.getQuery().select(q.status).from(q).where(q.id.eq(teamMatchId)).fetchOne()!!
}

// @SQLRestriction("deleted_at is null") 적용 — 소프트 삭제된 헤더는 조회에서 빠지므로 존재 여부로 종료를 확인한다.
private fun teamMatchExists(teamMatchId: Long): Boolean {
	val q: QTeamMatchEntity = QTeamMatchEntity.teamMatchEntity
	return IntegrationUtil.getQuery().select(q.id).from(q).where(q.id.eq(teamMatchId)).fetchOne() != null
}

private fun roomExists(chatRoomId: Long): Boolean {
	val q: QChatRoomEntity = QChatRoomEntity.chatRoomEntity
	return IntegrationUtil.getQuery().select(q.id).from(q).where(q.id.eq(chatRoomId)).fetchOne() != null
}

private fun memberStatus(chatRoomId: Long, userId: Long): ChatRoomMemberStatus {
	val q: QChatRoomMemberEntity = QChatRoomMemberEntity.chatRoomMemberEntity
	return IntegrationUtil.getQuery().select(q.status).from(q)
		.where(q.chatRoomId.eq(chatRoomId).and(q.userId.eq(userId))).fetchOne()!!
}

private fun chatMessages(chatRoomId: Long): List<ChatMessageEntity> {
	val q: QChatMessageEntity = QChatMessageEntity.chatMessageEntity
	return IntegrationUtil.getQuery().selectFrom(q).where(q.chatRoomId.eq(chatRoomId)).fetch()
}

private fun alarmsOf(userId: Long): List<AlarmEntity> {
	val alarm: QAlarmEntity = QAlarmEntity.alarmEntity
	return IntegrationUtil.getQuery().selectFrom(alarm).where(alarm.userId.eq(userId)).fetch()
}
```

- [ ] **Step 3: E2E 실행 (실패 → 통과)**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.match.EndTeamMatchE2ETest"`
Expected: 컨트롤러/서비스 미연결 시 FAIL → Step 1 반영 후 PASS

> 참고: `accessTokenFor`/`delete`/`bearer`/`AbstractIntegrationSupport`/`IntegrationUtil`/`ChatRoomEntityFixture`/`ChatRoomMemberEntityFixture`는 기존 E2E(`EndMatchE2ETest`·`TeamMatchPromotionOnAcceptE2ETest`)에서 그대로 쓰는 것이다. `TeamEntity`/`TeamMemberEntity`/`TeamMatchEntity`/`MatchedTeamEntity`는 픽스처가 없어 생성자로 직접 만든다(프로모션 E2E와 동일 방식).

- [ ] **Step 4: 전체 회귀 확인**

Run: `./gradlew :oneulsogae-api:test`
Expected: BUILD SUCCESSFUL (기존 테스트 포함 전부 통과)

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/match/TeamMatchController.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/EndTeamMatchE2ETest.kt
git commit -m "feat(match): 팀 매칭 종료 엔드포인트(DELETE /team-matches/v1/{teamMatchId}) + E2E"
```

---

## 프론트엔드 영향 (백엔드는 수정하지 않음 — 작업 후 사용자에게 전달)

- 신규 `DELETE /team-matches/v1/{teamMatchId}` 호출 추가(미팅/채팅 화면의 "매칭 종료" 액션).
- 신규 알림 타입 `MANY_TO_MANY_MATCH_ENDED` 표시 처리(문구/아이콘/`/` 라우팅).
- 응답 형식은 기존 `ApiResponse<Unit>`와 동일(`DELETE /matches/v1/{matchId}`와 같음).

## Self-Review

- **Spec coverage**: ① 검증=Task 2(`validateTerminable`)+Task 4(참가 팀 식별). ② matched_team 비활성+soft delete=Task 1·2(`leave`). ③ 우리 팀원 채팅 비활성=Task 4(`deactivate(... activeMemberIds())`). ④ 상대 팀 알림=Task 3·4(`TeamMatchEnded`). 엔드포인트=Task 5. 모두 매핑됨.
- **Placeholder scan**: 모든 step에 실제 코드/명령/기대결과 포함. 플레이스홀더 없음.
- **Type consistency**: `leave(teamId, now)`/`isLastActiveTeam(teamId)`/`allDeactivated()`/`TeamMatchEnded(teamMatchId, fromTeamId, recipientUserIds)`/`endTeamMatch(userId, teamMatchId)` 명칭이 Task 간 일치. `matchedTeamStatus`가 soft delete된 행에 `null` 반환하는 점 테스트에 반영.
