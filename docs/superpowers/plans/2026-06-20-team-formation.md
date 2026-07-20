# 미팅 팀 결성 완성(A 슬라이스) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 2:2 미팅 팀의 초대 수락(→FORMED)·거절/취소·해체와 "한 팀만" 제약을 구현해 팀 결성 라이프사이클을 완성한다.

**Architecture:** 헥사고날 + CQRS. 도메인 `Team`/`TeamMembers`에 상태 전이 행위를 추가하고, in-port UseCase별 `@Transactional` 서비스가 새 out-port `GetTeamPort`(조회)와 기존 `SaveTeamPort`(저장)를 조합한다. 비활성화는 솔로 `Match.delete` 패턴(status 전이 + soft delete)을 따른다. 수락↔취소 경합은 teamId 분산 락으로 직렬화한다.

**Tech Stack:** Kotlin 2.2.21 / Spring Boot 4 / Spring Data JPA / MySQL · Kotest(DescribeSpec) 도메인 유닛 · RestAssured + Testcontainers E2E.

## Global Constraints

- 모듈 의존: core는 인프라 비의존. infra 어댑터가 out-port 구현. Controller/Request/Response는 oneulsogae-api에만.
- 타입 명시: 변수·반환·람다 파라미터 타입 생략 금지(표현식 본문 포함).
- 도메인 검증: 서비스에 `if…throw` 나열 금지. 도메인 `validate<대상>(…)` 함수로 캡슐화. `now`는 파라미터 주입.
- CQRS: 명령 서비스 `@Transactional`. Get/Save 아웃포트 분리. 엔티티당 어댑터 하나(`TeamAdapter`가 Save·Get 함께 구현).
- 에러: 도메인별 `TeamErrorCode` enum + `BusinessException`.
- 테스트: 도메인 모델 → Kotest 유닛(`oneulsogae-api/src/test/.../domain/match`), api 경계 → E2E(`AbstractIntegrationSupport` + `IntegrationUtil`/픽스처 + RestAssured DSL). 리포지토리 직접 의존 금지.
- 빌드/테스트: `./gradlew :oneulsogae-api:test`(E2E·도메인 유닛 포함), 도메인만이면 `--tests` 필터 사용.

---

## File Structure

**도메인(core)**
- Modify `oneulsogae-core/.../match/command/domain/TeamMembers.kt` — `find`/`accept`/`allActive`/`deactivateAll`
- Modify `oneulsogae-core/.../match/command/domain/Team.kt` — `acceptInvitation`/`withdrawInvitation`/`disband` + `validate…`
- Modify `oneulsogae-core/.../match/TeamErrorCode.kt` — TEAM-005~009

**포트·서비스(core)**
- Create `oneulsogae-core/.../match/command/application/port/out/GetTeamPort.kt`
- Create `.../port/in/AcceptTeamInvitationUseCase.kt`, `WithdrawTeamInvitationUseCase.kt`, `DisbandTeamUseCase.kt`
- Create `.../application/AcceptTeamInvitationService.kt`, `WithdrawTeamInvitationService.kt`, `DisbandTeamService.kt`
- Modify `.../application/InviteTeamService.kt` — "한 팀만" 검증
- Modify `oneulsogae-core/.../common/lock/LockKeyConstraints.kt` — `TEAM_LIFECYCLE`

**인프라(infra)**
- Modify `oneulsogae-infra/.../match/command/adapter/TeamAdapter.kt` — `GetTeamPort` 구현
- Modify `oneulsogae-infra/.../match/command/repository/TeamMemberJpaRepository.kt` — `existsByUserId`

**API**
- Modify `oneulsogae-api/.../match/TeamController.kt` — 3개 엔드포인트

**테스트**
- Create `oneulsogae-api/src/test/.../domain/match/TeamMembersTest.kt`
- Modify `oneulsogae-api/src/test/.../domain/match/TeamTest.kt`
- Create `oneulsogae-api/src/test/.../api/match/AcceptTeamInvitationE2ETest.kt`, `WithdrawTeamInvitationE2ETest.kt`, `DisbandTeamE2ETest.kt`
- Modify `oneulsogae-api/src/test/.../api/match/InviteTeamE2ETest.kt` — 한 팀만 위반

---

## Task 1: TeamMembers 도메인 행위

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/TeamMembers.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/match/TeamMembersTest.kt`

**Interfaces:**
- Produces:
  - `TeamMembers.find(userId: Long): TeamMember?`
  - `TeamMembers.accept(userId: Long): TeamMembers` — 해당 userId 구성원만 ACTIVE로 교체
  - `TeamMembers.allActive(): Boolean`
  - `TeamMembers.deactivateAll(now: LocalDateTime): TeamMembers` — 전원 DEACTIVE + deletedAt

- [ ] **Step 1: Write the failing test**

Create `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/match/TeamMembersTest.kt`:

```kotlin
package com.org.oneulsogae.domain.match

import com.org.oneulsogae.common.match.TeamMemberStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.match.command.domain.TeamMember
import com.org.oneulsogae.core.match.command.domain.TeamMembers
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

/**
 * [TeamMembers] 일급 컬렉션 행위 유닛 테스트.
 */
class TeamMembersTest : DescribeSpec({

    val ownerId: Long = 1L
    val invitedId: Long = 2L

    fun invitingMembers(): TeamMembers =
        TeamMembers(
            listOf(
                TeamMember(teamId = 0, userId = ownerId, gender = Gender.MALE, status = TeamMemberStatus.ACTIVE),
                TeamMember(teamId = 0, userId = invitedId, gender = Gender.MALE, status = TeamMemberStatus.INVITED),
            ),
        )

    describe("find") {
        it("userId로 구성원을 찾고, 없으면 null이다") {
            invitingMembers().find(invitedId).shouldNotBeNull().userId shouldBe invitedId
            invitingMembers().find(999L) shouldBe null
        }
    }

    describe("accept") {
        it("해당 구성원만 ACTIVE로 바꾸고 나머지는 그대로 둔다") {
            val accepted: TeamMembers = invitingMembers().accept(invitedId)

            accepted.find(invitedId)!!.status shouldBe TeamMemberStatus.ACTIVE
            accepted.find(ownerId)!!.status shouldBe TeamMemberStatus.ACTIVE
        }
    }

    describe("allActive") {
        it("전원 ACTIVE면 true, 하나라도 아니면 false다") {
            invitingMembers().allActive() shouldBe false
            invitingMembers().accept(invitedId).allActive() shouldBe true
        }
    }

    describe("deactivateAll") {
        it("전원을 DEACTIVE + deletedAt으로 표시한다") {
            val now: LocalDateTime = LocalDateTime.of(2026, 6, 20, 12, 0)

            val deactivated: TeamMembers = invitingMembers().deactivateAll(now)

            deactivated.values.forEach { member: TeamMember ->
                member.status shouldBe TeamMemberStatus.DEACTIVE
                member.deletedAt shouldBe now
            }
        }
    }
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.match.TeamMembersTest"`
Expected: 컴파일 실패 — `find`/`accept`/`allActive`/`deactivateAll` 미정의.

- [ ] **Step 3: Add behaviors to TeamMembers**

Add these methods inside the `TeamMembers` class body (before the `companion object`), and add the import `import java.time.LocalDateTime` at the top:

```kotlin
	/** [userId] 구성원을 찾는다. 없으면 null. */
	fun find(userId: Long): TeamMember? =
		values.firstOrNull { member: TeamMember -> member.userId == userId }

	/** [userId] 구성원만 ACTIVE로 전환한 새 컬렉션. (나머지는 그대로) */
	fun accept(userId: Long): TeamMembers =
		TeamMembers(
			values.map { member: TeamMember ->
				if (member.userId == userId) member.copy(status = TeamMemberStatus.ACTIVE) else member
			},
		)

	/** 모든 구성원이 ACTIVE인지 여부. */
	fun allActive(): Boolean =
		values.all { member: TeamMember -> member.status == TeamMemberStatus.ACTIVE }

	/** 전원을 비활성(DEACTIVE) + 소프트 삭제([now]) 표시한 새 컬렉션. (팀 해체·초대취소 시) */
	fun deactivateAll(now: LocalDateTime): TeamMembers =
		TeamMembers(
			values.map { member: TeamMember -> member.copy(status = TeamMemberStatus.DEACTIVE, deletedAt = now) },
		)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.match.TeamMembersTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/TeamMembers.kt \
  oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/match/TeamMembersTest.kt
git commit -m "feat: TeamMembers에 find·accept·allActive·deactivateAll 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: TeamErrorCode 5종 + Team.acceptInvitation

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/TeamErrorCode.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/Team.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/match/TeamTest.kt`

**Interfaces:**
- Consumes: `TeamMembers.find/accept/allActive` (Task 1)
- Produces:
  - `TeamErrorCode.TEAM_NOT_FOUND/NOT_TEAM_MEMBER/NOT_INVITED_MEMBER/INVALID_TEAM_STATUS/ALREADY_IN_TEAM`
  - `Team.acceptInvitation(userId: Long): Team`

- [ ] **Step 1: Add error codes**

Add to `TeamErrorCode` enum (after `MUST_INVITE_SAME_GENDER`), keeping the trailing comma style. Add `import org.springframework.http.HttpStatus` is already present:

```kotlin
	TEAM_NOT_FOUND("TEAM-005", "팀을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
	NOT_TEAM_MEMBER("TEAM-006", "해당 팀의 구성원이 아닙니다.", HttpStatus.FORBIDDEN),
	NOT_INVITED_MEMBER("TEAM-007", "초대를 받은 구성원만 수락할 수 있습니다.", HttpStatus.BAD_REQUEST),
	INVALID_TEAM_STATUS("TEAM-008", "현재 팀 상태에서 할 수 없는 작업입니다.", HttpStatus.CONFLICT),
	ALREADY_IN_TEAM("TEAM-009", "이미 다른 팀에 속해 있습니다.", HttpStatus.CONFLICT),
```

- [ ] **Step 2: Write the failing test**

Append to `TeamTest.kt`'s top-level `DescribeSpec` body (before the closing `})`), using existing imports plus add `import com.org.oneulsogae.common.match.TeamMemberStatus` (already imported) and `import com.org.oneulsogae.core.match.command.domain.TeamMembers` and `import com.org.oneulsogae.core.match.command.domain.TeamMember`:

```kotlin
	describe("acceptInvitation - 초대 수락") {
		fun invitingTeam(): Team =
			Team(
				name = "우리팀",
				members = TeamMembers(
					listOf(
						TeamMember(teamId = 0, userId = ownerId, gender = Gender.MALE, status = TeamMemberStatus.ACTIVE),
						TeamMember(teamId = 0, userId = invitedUserId, gender = Gender.MALE, status = TeamMemberStatus.INVITED),
					),
				),
				status = TeamStatus.INVITING,
			)

		it("초대받은 구성원이 수락하면 ACTIVE가 되고 전원 ACTIVE이므로 FORMED로 전이한다") {
			val formed: Team = invitingTeam().acceptInvitation(invitedUserId)

			formed.status shouldBe TeamStatus.FORMED
			formed.members.find(invitedUserId)!!.status shouldBe TeamMemberStatus.ACTIVE
		}

		it("INVITING이 아니면 INVALID_TEAM_STATUS를 던진다") {
			val ex: BusinessException = shouldThrow {
				invitingTeam().copy(status = TeamStatus.FORMED).acceptInvitation(invitedUserId)
			}

			ex.errorCode shouldBe TeamErrorCode.INVALID_TEAM_STATUS
		}

		it("구성원이 아니면 NOT_TEAM_MEMBER를 던진다") {
			val ex: BusinessException = shouldThrow { invitingTeam().acceptInvitation(999L) }

			ex.errorCode shouldBe TeamErrorCode.NOT_TEAM_MEMBER
		}

		it("이미 ACTIVE인(초대받지 않은) 구성원이 수락하면 NOT_INVITED_MEMBER를 던진다") {
			val ex: BusinessException = shouldThrow { invitingTeam().acceptInvitation(ownerId) }

			ex.errorCode shouldBe TeamErrorCode.NOT_INVITED_MEMBER
		}
	}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.match.TeamTest"`
Expected: 컴파일 실패 — `acceptInvitation` 미정의.

- [ ] **Step 4: Implement Team.acceptInvitation**

Add to `Team` class body (after the `isClosed`-style properties / before `companion object`). The imports `TeamMemberStatus`, `TeamStatus`, `BusinessException`, `TeamErrorCode` are already present:

```kotlin
	/**
	 * 초대받은([TeamMemberStatus.INVITED]) 구성원([userId])이 초대를 수락한다.
	 * 그 구성원을 ACTIVE로 전환하고, 전원 ACTIVE가 되면 팀을 [TeamStatus.FORMED]로 전이한 새 모델을 반환한다.
	 * 상태·구성원 자격은 [validateAcceptable]로 검증한다.
	 */
	fun acceptInvitation(userId: Long): Team {
		validateAcceptable(userId)
		val accepted: TeamMembers = members.accept(userId)
		return copy(
			members = accepted,
			status = if (accepted.allActive()) TeamStatus.FORMED else status,
		)
	}

	// INVITING 상태가 아니면 INVALID_TEAM_STATUS, 구성원이 아니면 NOT_TEAM_MEMBER, INVITED 상태가 아니면 NOT_INVITED_MEMBER.
	private fun validateAcceptable(userId: Long) {
		if (status != TeamStatus.INVITING) {
			throw BusinessException(TeamErrorCode.INVALID_TEAM_STATUS)
		}
		val member: TeamMember = members.find(userId)
			?: throw BusinessException(TeamErrorCode.NOT_TEAM_MEMBER)
		if (member.status != TeamMemberStatus.INVITED) {
			throw BusinessException(TeamErrorCode.NOT_INVITED_MEMBER)
		}
	}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.match.TeamTest"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/TeamErrorCode.kt \
  oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/Team.kt \
  oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/match/TeamTest.kt
git commit -m "feat: 팀 초대 수락 도메인(acceptInvitation)·에러코드 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Team.withdrawInvitation / disband

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/Team.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/match/TeamTest.kt`

**Interfaces:**
- Consumes: `TeamMembers.deactivateAll` (Task 1), `TeamMembers.isMember` (기존), TeamErrorCode (Task 2)
- Produces:
  - `Team.withdrawInvitation(userId: Long, now: LocalDateTime): Team`
  - `Team.disband(userId: Long, now: LocalDateTime): Team`

- [ ] **Step 1: Write the failing test**

Append to `TeamTest.kt` body. Add `import java.time.LocalDateTime` at top:

```kotlin
	describe("withdrawInvitation - 거절·초대취소") {
		val now: LocalDateTime = LocalDateTime.of(2026, 6, 20, 12, 0)

		fun invitingTeam(): Team =
			Team(
				name = "우리팀",
				members = TeamMembers(
					listOf(
						TeamMember(teamId = 0, userId = ownerId, gender = Gender.MALE, status = TeamMemberStatus.ACTIVE),
						TeamMember(teamId = 0, userId = invitedUserId, gender = Gender.MALE, status = TeamMemberStatus.INVITED),
					),
				),
				status = TeamStatus.INVITING,
			)

		it("INVITING 팀의 구성원이 철회하면 DEACTIVATED + 전원 비활성·soft delete가 된다") {
			val deactivated: Team = invitingTeam().withdrawInvitation(ownerId, now)

			deactivated.status shouldBe TeamStatus.DEACTIVATED
			deactivated.deletedAt shouldBe now
			deactivated.members.values.forEach { it.status shouldBe TeamMemberStatus.DEACTIVE }
		}

		it("INVITING이 아니면 INVALID_TEAM_STATUS를 던진다") {
			val ex: BusinessException = shouldThrow {
				invitingTeam().copy(status = TeamStatus.FORMED).withdrawInvitation(ownerId, now)
			}
			ex.errorCode shouldBe TeamErrorCode.INVALID_TEAM_STATUS
		}

		it("구성원이 아니면 NOT_TEAM_MEMBER를 던진다") {
			val ex: BusinessException = shouldThrow { invitingTeam().withdrawInvitation(999L, now) }
			ex.errorCode shouldBe TeamErrorCode.NOT_TEAM_MEMBER
		}
	}

	describe("disband - 해체·떠나기") {
		val now: LocalDateTime = LocalDateTime.of(2026, 6, 20, 12, 0)

		fun formedTeam(): Team =
			Team(
				name = "우리팀",
				members = TeamMembers(
					listOf(
						TeamMember(teamId = 0, userId = ownerId, gender = Gender.MALE, status = TeamMemberStatus.ACTIVE),
						TeamMember(teamId = 0, userId = invitedUserId, gender = Gender.MALE, status = TeamMemberStatus.ACTIVE),
					),
				),
				status = TeamStatus.FORMED,
			)

		it("FORMED 팀의 구성원이 해체하면 DEACTIVATED + soft delete가 된다") {
			val deactivated: Team = formedTeam().disband(invitedUserId, now)

			deactivated.status shouldBe TeamStatus.DEACTIVATED
			deactivated.deletedAt shouldBe now
		}

		it("FORMED가 아니면 INVALID_TEAM_STATUS를 던진다") {
			val ex: BusinessException = shouldThrow {
				formedTeam().copy(status = TeamStatus.INVITING).disband(ownerId, now)
			}
			ex.errorCode shouldBe TeamErrorCode.INVALID_TEAM_STATUS
		}
	}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.match.TeamTest"`
Expected: 컴파일 실패 — `withdrawInvitation`/`disband` 미정의.

- [ ] **Step 3: Implement withdrawInvitation/disband**

Add to `Team` class body (near `acceptInvitation`). Add `import java.time.LocalDateTime` at top of `Team.kt`:

```kotlin
	/**
	 * 초대 단계([TeamStatus.INVITING])의 팀을 철회한다. (초대받은 사람의 거절 / 초대자의 취소 공통)
	 * 팀을 [TeamStatus.DEACTIVATED]로 전이하고 팀·구성원을 [now]로 소프트 삭제한 새 모델을 반환한다.
	 */
	fun withdrawInvitation(userId: Long, now: LocalDateTime): Team {
		validateWithdrawable(userId)
		return deactivate(now)
	}

	/**
	 * 결성([TeamStatus.FORMED])된 팀을 해체한다. (구성원이 떠나면 2인 팀이 유지될 수 없어 팀 전체를 비활성화)
	 * 팀을 [TeamStatus.DEACTIVATED]로 전이하고 팀·구성원을 [now]로 소프트 삭제한 새 모델을 반환한다.
	 */
	fun disband(userId: Long, now: LocalDateTime): Team {
		validateDisbandable(userId)
		return deactivate(now)
	}

	// INVITING 상태가 아니면 INVALID_TEAM_STATUS, 구성원이 아니면 NOT_TEAM_MEMBER.
	private fun validateWithdrawable(userId: Long) {
		if (status != TeamStatus.INVITING) {
			throw BusinessException(TeamErrorCode.INVALID_TEAM_STATUS)
		}
		if (!members.isMember(userId)) {
			throw BusinessException(TeamErrorCode.NOT_TEAM_MEMBER)
		}
	}

	// FORMED 상태가 아니면 INVALID_TEAM_STATUS, 구성원이 아니면 NOT_TEAM_MEMBER.
	private fun validateDisbandable(userId: Long) {
		if (status != TeamStatus.FORMED) {
			throw BusinessException(TeamErrorCode.INVALID_TEAM_STATUS)
		}
		if (!members.isMember(userId)) {
			throw BusinessException(TeamErrorCode.NOT_TEAM_MEMBER)
		}
	}

	// 팀과 구성원을 비활성·소프트 삭제한다. (withdraw/disband 공통)
	private fun deactivate(now: LocalDateTime): Team =
		copy(status = TeamStatus.DEACTIVATED, deletedAt = now, members = members.deactivateAll(now))
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.match.TeamTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/Team.kt \
  oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/match/TeamTest.kt
git commit -m "feat: 팀 철회(거절·취소)·해체 도메인(withdrawInvitation·disband) 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: GetTeamPort + TeamAdapter 조회 구현

**Files:**
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/port/out/GetTeamPort.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/repository/TeamMemberJpaRepository.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/adapter/TeamAdapter.kt`

**Interfaces:**
- Produces:
  - `GetTeamPort.findById(teamId: Long): Team?`
  - `GetTeamPort.existsActiveTeamMember(userId: Long): Boolean`
  - `TeamMemberJpaRepository.existsByUserId(userId: Long): Boolean`

> 인프라 어댑터는 이 코드베이스에서 유닛 테스트하지 않는다(리포지토리 직접 의존 금지). 동작은 Task 5~8의 E2E가 검증한다. 이 태스크의 게이트는 **컴파일 통과 + 전체 빌드 그린**이다.

- [ ] **Step 1: Create GetTeamPort**

```kotlin
package com.org.oneulsogae.core.match.command.application.port.out

import com.org.oneulsogae.core.match.command.domain.Team

/**
 * 팀(헤더+구성원) 조회 아웃포트. (CQRS상 저장은 [SaveTeamPort]와 분리)
 * 구현은 infra의 [com.org.oneulsogae.infra.match.command.adapter.TeamAdapter]가 담당한다.
 */
interface GetTeamPort {

	/** 팀 애그리거트(헤더+구성원)를 조회한다. 없으면 null. (소프트 삭제된 팀은 제외) */
	fun findById(teamId: Long): Team?

	/** [userId]가 활성(삭제되지 않은) 팀 구성원으로 속해 있는지 여부. ("한 팀만" 제약 판정) */
	fun existsActiveTeamMember(userId: Long): Boolean
}
```

- [ ] **Step 2: Add repository method**

Add to `TeamMemberJpaRepository` interface body:

```kotlin
	/** [userId]가 (삭제되지 않은) 팀 구성원으로 존재하는지 여부. (@SQLRestriction이 삭제행을 제외) */
	fun existsByUserId(userId: Long): Boolean
```

- [ ] **Step 3: Implement GetTeamPort in TeamAdapter**

Modify `TeamAdapter` to also implement `GetTeamPort`. Add imports `import com.org.oneulsogae.core.match.command.application.port.out.GetTeamPort` and `import com.org.oneulsogae.infra.match.command.entity.TeamMemberEntity`. Change the class declaration and add the two methods:

```kotlin
@Component
class TeamAdapter(
	private val teamJpaRepository: TeamJpaRepository,
	private val teamMemberJpaRepository: TeamMemberJpaRepository,
) : SaveTeamPort, GetTeamPort {

	// ... 기존 save(team: Team) 그대로 유지 ...

	/** 팀 헤더와 구성원 행들을 함께 조회해 도메인으로 조립한다. 헤더가 없으면 null. */
	override fun findById(teamId: Long): Team? {
		val teamEntity: TeamEntity = teamJpaRepository.findById(teamId).orElse(null) ?: return null
		val members: TeamMembers = TeamMembers(
			teamMemberJpaRepository.findByTeamId(teamId).map { entity: TeamMemberEntity -> entity.toDomain() },
		)
		return teamEntity.toDomain(members)
	}

	/** 삭제되지 않은 team_member 행 존재 여부로 활성 팀 소속을 판정한다. */
	override fun existsActiveTeamMember(userId: Long): Boolean =
		teamMemberJpaRepository.existsByUserId(userId)
}
```

- [ ] **Step 4: Verify compilation / full build**

Run: `./gradlew :oneulsogae-infra:compileKotlin :oneulsogae-core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/port/out/GetTeamPort.kt \
  oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/repository/TeamMemberJpaRepository.kt \
  oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/adapter/TeamAdapter.kt
git commit -m "feat: GetTeamPort(findById·existsActiveTeamMember)와 TeamAdapter 조회 구현

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: 초대 수락 슬라이스 + 락 상수 + E2E

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/common/lock/LockKeyConstraints.kt`
- Create: `oneulsogae-core/.../match/command/application/port/in/AcceptTeamInvitationUseCase.kt`
- Create: `oneulsogae-core/.../match/command/application/AcceptTeamInvitationService.kt`
- Modify: `oneulsogae-api/.../match/TeamController.kt`
- Test: `oneulsogae-api/src/test/.../api/match/AcceptTeamInvitationE2ETest.kt`

**Interfaces:**
- Consumes: `GetTeamPort` (Task 4), `SaveTeamPort` (기존), `Team.acceptInvitation` (Task 2)
- Produces:
  - `LockKeyConstraints.TEAM_LIFECYCLE: String`
  - `AcceptTeamInvitationUseCase.accept(userId: Long, teamId: Long): Team`

- [ ] **Step 1: Add lock prefix**

Add to `LockKeyConstraints` object:

```kotlin
	/**
	 * 팀 결성 라이프사이클(수락/철회/해체) 처리 락. teamId로 잠가 한 팀의 상태 변경을 직렬화한다.
	 * 수락(invited)과 초대취소(owner) 동시 요청으로 인한 FORMED↔DEACTIVATED lost update를 막는다.
	 */
	const val TEAM_LIFECYCLE: String = "TEAM_LIFECYCLE"
```

- [ ] **Step 2: Create the use case**

```kotlin
package com.org.oneulsogae.core.match.command.application.port.`in`

import com.org.oneulsogae.core.match.command.domain.Team

/**
 * 팀 초대 수락 유스케이스(인포트).
 * 초대받은 사용자([userId])가 팀([teamId]) 초대를 수락해 구성원이 되고, 전원 수락 시 팀이 결성(FORMED)된다.
 */
interface AcceptTeamInvitationUseCase {

	/** [userId]가 [teamId] 팀 초대를 수락하고, 갱신된 팀을 반환한다. */
	fun accept(userId: Long, teamId: Long): Team
}
```

- [ ] **Step 3: Write the failing E2E test**

Create `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/AcceptTeamInvitationE2ETest.kt`:

```kotlin
package com.org.oneulsogae.api.match

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.common.match.TeamMemberStatus
import com.org.oneulsogae.common.match.TeamStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.MatchUserEntityFixture
import com.org.oneulsogae.infra.match.command.entity.QMatchUserEntity
import com.org.oneulsogae.infra.match.command.entity.QTeamEntity
import com.org.oneulsogae.infra.match.command.entity.QTeamMemberEntity
import com.org.oneulsogae.infra.match.command.entity.TeamEntity
import com.org.oneulsogae.infra.match.command.entity.TeamMemberEntity
import io.kotest.matchers.shouldBe

/**
 * `POST /teams/v1/{teamId}/acceptance` E2E 테스트. (초대 수락)
 * 초대받은 사용자가 수락하면 본인이 ACTIVE가 되고 전원 ACTIVE이므로 팀이 FORMED로 전이한다.
 */
class AcceptTeamInvitationE2ETest : AbstractIntegrationSupport({

	fun persistMatchUser(userId: Long, gender: Gender) {
		IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = gender))
	}

	// 팀을 결성(초대)하고 teamId를 돌려준다.
	fun inviteTeam(ownerId: Long, invitedUserId: Long): Long {
		persistMatchUser(ownerId, Gender.MALE)
		persistMatchUser(invitedUserId, Gender.MALE)
		return post("/teams/v1") {
			bearer(accessTokenFor(ownerId))
			jsonBody("""{"invitedUserId": $invitedUserId, "name": "우리팀", "introduction": null}""")
		}.extract().path<Int>("data.teamId").toLong()
	}

	describe("POST /teams/v1/{teamId}/acceptance") {

		context("초대받은 사용자가 수락하면") {
			it("본인이 ACTIVE가 되고 팀이 FORMED가 된다 (200)") {
				val ownerId = 2001L
				val invitedUserId = 2002L
				val teamId: Long = inviteTeam(ownerId, invitedUserId)

				post("/teams/v1/$teamId/acceptance") {
					bearer(accessTokenFor(invitedUserId))
				} expect {
					status(200)
					body("success", true)
					body("data.status", TeamStatus.FORMED.name)
				}

				val members: List<TeamMemberEntity> = teamMembersOf(teamId)
				members.all { it.status == TeamMemberStatus.ACTIVE } shouldBe true
			}
		}

		context("초대받지 않은(이미 ACTIVE인) owner가 수락하면") {
			it("400(TEAM-007)을 반환한다") {
				val ownerId = 2003L
				val invitedUserId = 2004L
				val teamId: Long = inviteTeam(ownerId, invitedUserId)

				post("/teams/v1/$teamId/acceptance") {
					bearer(accessTokenFor(ownerId))
				} expect {
					status(400)
					body("error.code", "TEAM-007")
				}
			}
		}

		context("없는 팀을 수락하면") {
			it("404(TEAM-005)를 반환한다") {
				val userId = 2005L
				persistMatchUser(userId, Gender.MALE)

				post("/teams/v1/999999/acceptance") {
					bearer(accessTokenFor(userId))
				} expect {
					status(404)
					body("error.code", "TEAM-005")
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
		IntegrationUtil.deleteAll(QTeamEntity.teamEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
	}
})

private fun teamMembersOf(teamId: Long): List<TeamMemberEntity> {
	val member: QTeamMemberEntity = QTeamMemberEntity.teamMemberEntity
	return IntegrationUtil.getQuery().selectFrom(member).where(member.teamId.eq(teamId)).fetch()
}
```

> 참고: `extract().path<Int>(...)` 와 `expect { }` 는 `RestAssuredDsl`가 제공한다(`InviteTeamE2ETest` 동일 패턴). `import com.org.oneulsogae.infra.match.command.entity.TeamEntity` 는 사용하지 않으면 제거.

- [ ] **Step 4: Run E2E to verify it fails**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.match.AcceptTeamInvitationE2ETest"`
Expected: 컴파일 실패(엔드포인트·서비스 미존재) 또는 404/실패.

- [ ] **Step 5: Implement the service**

Create `AcceptTeamInvitationService.kt`:

```kotlin
package com.org.oneulsogae.core.match.command.application

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.common.lock.DistributedLock
import com.org.oneulsogae.core.common.lock.LockKeyConstraints
import com.org.oneulsogae.core.match.TeamErrorCode
import com.org.oneulsogae.core.match.command.application.port.`in`.AcceptTeamInvitationUseCase
import com.org.oneulsogae.core.match.command.application.port.out.GetTeamPort
import com.org.oneulsogae.core.match.command.application.port.out.SaveTeamPort
import com.org.oneulsogae.core.match.command.domain.Team
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [AcceptTeamInvitationUseCase] 구현. 초대받은 사용자가 팀 초대를 수락한다.
 * 팀을 조회해 [Team.acceptInvitation]으로 상태를 전이(전원 수락 시 FORMED)한 뒤 저장한다.
 * 수락(invited)↔초대취소(owner) 동시 요청 경합을 막기 위해 teamId 분산 락으로 직렬화한다. (waitTime=0)
 */
@Service
class AcceptTeamInvitationService(
	private val getTeamPort: GetTeamPort,
	private val saveTeamPort: SaveTeamPort,
) : AcceptTeamInvitationUseCase {

	@DistributedLock(prefix = LockKeyConstraints.TEAM_LIFECYCLE, keys = ["#teamId"], waitTime = 0)
	@Transactional
	override fun accept(userId: Long, teamId: Long): Team {
		val team: Team = getTeamPort.findById(teamId)
			?: throw BusinessException(TeamErrorCode.TEAM_NOT_FOUND)
		return saveTeamPort.save(team.acceptInvitation(userId))
	}
}
```

- [ ] **Step 6: Add the controller endpoint**

In `TeamController`, inject `AcceptTeamInvitationUseCase` and add the mapping. Add imports `import com.org.oneulsogae.core.match.command.application.port.in.AcceptTeamInvitationUseCase`, `import org.springframework.web.bind.annotation.PathVariable`:

```kotlin
@RestController
@RequestMapping("/teams/v1")
class TeamController(
	private val inviteTeamUseCase: InviteTeamUseCase,
	private val acceptTeamInvitationUseCase: AcceptTeamInvitationUseCase,
) {

	// ... 기존 invite() 그대로 ...

	/** 초대받은 사용자가 팀 초대를 수락한다. 전원 수락 시 팀이 결성(FORMED)된다. */
	@PostMapping("/{teamId}/acceptance")
	fun accept(
		@LoginUser user: AuthUser,
		@PathVariable teamId: Long,
	): ApiResponse<TeamResponse> =
		ApiResponse.success(TeamResponse.of(acceptTeamInvitationUseCase.accept(user.id, teamId)))
}
```

- [ ] **Step 7: Run E2E to verify it passes**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.match.AcceptTeamInvitationE2ETest"`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/common/lock/LockKeyConstraints.kt \
  oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/port/in/AcceptTeamInvitationUseCase.kt \
  oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/AcceptTeamInvitationService.kt \
  oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/match/TeamController.kt \
  oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/AcceptTeamInvitationE2ETest.kt
git commit -m "feat: 팀 초대 수락 엔드포인트(POST /teams/v1/{teamId}/acceptance)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: 거절·초대취소 슬라이스 + E2E

**Files:**
- Create: `oneulsogae-core/.../match/command/application/port/in/WithdrawTeamInvitationUseCase.kt`
- Create: `oneulsogae-core/.../match/command/application/WithdrawTeamInvitationService.kt`
- Modify: `oneulsogae-api/.../match/TeamController.kt`
- Test: `oneulsogae-api/src/test/.../api/match/WithdrawTeamInvitationE2ETest.kt`

**Interfaces:**
- Consumes: `GetTeamPort`/`SaveTeamPort`, `Team.withdrawInvitation` (Task 3), `LockKeyConstraints.TEAM_LIFECYCLE` (Task 5)
- Produces: `WithdrawTeamInvitationUseCase.withdraw(userId: Long, teamId: Long): Team`

- [ ] **Step 1: Create the use case**

```kotlin
package com.org.oneulsogae.core.match.command.application.port.`in`

import com.org.oneulsogae.core.match.command.domain.Team

/**
 * 팀 초대 철회 유스케이스(인포트). 초대 단계(INVITING)의 거절(초대받은 사람)·취소(초대자)를 함께 처리한다.
 * 결과로 팀이 비활성화(DEACTIVATED)되고 소프트 삭제된다.
 */
interface WithdrawTeamInvitationUseCase {

	/** [userId]가 [teamId] 팀의 초대를 철회(거절/취소)하고, 비활성화된 팀을 반환한다. */
	fun withdraw(userId: Long, teamId: Long): Team
}
```

- [ ] **Step 2: Write the failing E2E test**

Create `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/WithdrawTeamInvitationE2ETest.kt`:

```kotlin
package com.org.oneulsogae.api.match

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.delete
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.MatchUserEntityFixture
import com.org.oneulsogae.infra.match.command.entity.QMatchUserEntity
import com.org.oneulsogae.infra.match.command.entity.QTeamEntity
import com.org.oneulsogae.infra.match.command.entity.QTeamMemberEntity
import com.org.oneulsogae.infra.match.command.entity.TeamEntity
import io.kotest.matchers.shouldBe

/**
 * `DELETE /teams/v1/{teamId}/invitation` E2E 테스트. (초대 거절·취소)
 * INVITING 팀을 철회하면 팀이 소프트 삭제되어 활성 조회에서 사라진다.
 */
class WithdrawTeamInvitationE2ETest : AbstractIntegrationSupport({

	fun persistMatchUser(userId: Long, gender: Gender) {
		IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = gender))
	}

	fun inviteTeam(ownerId: Long, invitedUserId: Long): Long {
		persistMatchUser(ownerId, Gender.MALE)
		persistMatchUser(invitedUserId, Gender.MALE)
		return post("/teams/v1") {
			bearer(accessTokenFor(ownerId))
			jsonBody("""{"invitedUserId": $invitedUserId, "name": "우리팀", "introduction": null}""")
		}.extract().path<Int>("data.teamId").toLong()
	}

	describe("DELETE /teams/v1/{teamId}/invitation") {

		context("초대받은 사람이 거절하면") {
			it("팀이 비활성화되어 활성 조회에서 사라진다 (200)") {
				val ownerId = 3001L
				val invitedUserId = 3002L
				val teamId: Long = inviteTeam(ownerId, invitedUserId)

				delete("/teams/v1/$teamId/invitation") {
					bearer(accessTokenFor(invitedUserId))
				} expect {
					status(200)
					body("success", true)
				}

				allTeams().size shouldBe 0
			}
		}

		context("초대자(owner)가 취소하면") {
			it("팀이 비활성화된다 (200)") {
				val ownerId = 3003L
				val invitedUserId = 3004L
				val teamId: Long = inviteTeam(ownerId, invitedUserId)

				delete("/teams/v1/$teamId/invitation") {
					bearer(accessTokenFor(ownerId))
				} expect {
					status(200)
				}

				allTeams().size shouldBe 0
			}
		}

		context("구성원이 아닌 사용자가 철회하면") {
			it("403(TEAM-006)을 반환한다") {
				val ownerId = 3005L
				val invitedUserId = 3006L
				val strangerId = 3007L
				val teamId: Long = inviteTeam(ownerId, invitedUserId)
				persistMatchUser(strangerId, Gender.MALE)

				delete("/teams/v1/$teamId/invitation") {
					bearer(accessTokenFor(strangerId))
				} expect {
					status(403)
					body("error.code", "TEAM-006")
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
		IntegrationUtil.deleteAll(QTeamEntity.teamEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
	}
})

// 소프트 삭제되지 않은(활성) 팀 전체. @SQLRestriction이 삭제행을 제외한다.
private fun allTeams(): List<TeamEntity> {
	val team: QTeamEntity = QTeamEntity.teamEntity
	return IntegrationUtil.getQuery().selectFrom(team).fetch()
}
```

- [ ] **Step 3: Run E2E to verify it fails**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.match.WithdrawTeamInvitationE2ETest"`
Expected: 컴파일 실패/404.

- [ ] **Step 4: Implement the service**

Create `WithdrawTeamInvitationService.kt`:

```kotlin
package com.org.oneulsogae.core.match.command.application

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.common.lock.DistributedLock
import com.org.oneulsogae.core.common.lock.LockKeyConstraints
import com.org.oneulsogae.core.match.TeamErrorCode
import com.org.oneulsogae.core.match.command.application.port.`in`.WithdrawTeamInvitationUseCase
import com.org.oneulsogae.core.match.command.application.port.out.GetTeamPort
import com.org.oneulsogae.core.match.command.application.port.out.SaveTeamPort
import com.org.oneulsogae.core.match.command.domain.Team
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [WithdrawTeamInvitationUseCase] 구현. INVITING 팀의 초대를 철회(거절/취소)한다.
 * 팀을 조회해 [Team.withdrawInvitation]으로 비활성화(DEACTIVATED + soft delete)한 뒤 저장한다.
 * teamId 분산 락으로 수락 등 동시 상태 변경과 직렬화한다.
 */
@Service
class WithdrawTeamInvitationService(
	private val getTeamPort: GetTeamPort,
	private val saveTeamPort: SaveTeamPort,
) : WithdrawTeamInvitationUseCase {

	@DistributedLock(prefix = LockKeyConstraints.TEAM_LIFECYCLE, keys = ["#teamId"], waitTime = 0)
	@Transactional
	override fun withdraw(userId: Long, teamId: Long): Team {
		val team: Team = getTeamPort.findById(teamId)
			?: throw BusinessException(TeamErrorCode.TEAM_NOT_FOUND)
		return saveTeamPort.save(team.withdrawInvitation(userId, LocalDateTime.now()))
	}
}
```

- [ ] **Step 5: Add the controller endpoint**

In `TeamController`, inject `WithdrawTeamInvitationUseCase` and add (add imports for the use case and `DeleteMapping`):

```kotlin
	/** 초대 단계(INVITING) 팀의 초대를 철회한다. (초대받은 사람의 거절 / 초대자의 취소) */
	@DeleteMapping("/{teamId}/invitation")
	fun withdrawInvitation(
		@LoginUser user: AuthUser,
		@PathVariable teamId: Long,
	): ApiResponse<TeamResponse> =
		ApiResponse.success(TeamResponse.of(withdrawTeamInvitationUseCase.withdraw(user.id, teamId)))
```

- [ ] **Step 6: Run E2E to verify it passes**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.match.WithdrawTeamInvitationE2ETest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/port/in/WithdrawTeamInvitationUseCase.kt \
  oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/WithdrawTeamInvitationService.kt \
  oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/match/TeamController.kt \
  oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/WithdrawTeamInvitationE2ETest.kt
git commit -m "feat: 팀 초대 거절·취소 엔드포인트(DELETE /teams/v1/{teamId}/invitation)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 7: 해체·떠나기 슬라이스 + E2E

**Files:**
- Create: `oneulsogae-core/.../match/command/application/port/in/DisbandTeamUseCase.kt`
- Create: `oneulsogae-core/.../match/command/application/DisbandTeamService.kt`
- Modify: `oneulsogae-api/.../match/TeamController.kt`
- Test: `oneulsogae-api/src/test/.../api/match/DisbandTeamE2ETest.kt`

**Interfaces:**
- Consumes: `GetTeamPort`/`SaveTeamPort`, `Team.disband` (Task 3), `LockKeyConstraints.TEAM_LIFECYCLE`
- Produces: `DisbandTeamUseCase.disband(userId: Long, teamId: Long): Team`

- [ ] **Step 1: Create the use case**

```kotlin
package com.org.oneulsogae.core.match.command.application.port.`in`

import com.org.oneulsogae.core.match.command.domain.Team

/**
 * 팀 해체 유스케이스(인포트). 결성(FORMED)된 팀의 구성원이 떠나면 팀 전체를 비활성화(DEACTIVATED)한다.
 */
interface DisbandTeamUseCase {

	/** [userId]가 속한 [teamId] 팀을 해체하고, 비활성화된 팀을 반환한다. */
	fun disband(userId: Long, teamId: Long): Team
}
```

- [ ] **Step 2: Write the failing E2E test**

Create `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/DisbandTeamE2ETest.kt`:

```kotlin
package com.org.oneulsogae.api.match

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.delete
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.MatchUserEntityFixture
import com.org.oneulsogae.infra.match.command.entity.QMatchUserEntity
import com.org.oneulsogae.infra.match.command.entity.QTeamEntity
import com.org.oneulsogae.infra.match.command.entity.QTeamMemberEntity
import com.org.oneulsogae.infra.match.command.entity.TeamEntity
import io.kotest.matchers.shouldBe

/**
 * `DELETE /teams/v1/{teamId}` E2E 테스트. (결성된 팀 해체·떠나기)
 * FORMED 팀의 구성원이 해체하면 팀이 소프트 삭제되어 활성 조회에서 사라진다.
 */
class DisbandTeamE2ETest : AbstractIntegrationSupport({

	fun persistMatchUser(userId: Long, gender: Gender) {
		IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = gender))
	}

	// 결성(FORMED)까지 진행한 팀의 teamId를 돌려준다. (초대 → 수락)
	fun formedTeam(ownerId: Long, invitedUserId: Long): Long {
		persistMatchUser(ownerId, Gender.MALE)
		persistMatchUser(invitedUserId, Gender.MALE)
		val teamId: Long = post("/teams/v1") {
			bearer(accessTokenFor(ownerId))
			jsonBody("""{"invitedUserId": $invitedUserId, "name": "우리팀", "introduction": null}""")
		}.extract().path<Int>("data.teamId").toLong()
		post("/teams/v1/$teamId/acceptance") { bearer(accessTokenFor(invitedUserId)) }
		return teamId
	}

	describe("DELETE /teams/v1/{teamId}") {

		context("FORMED 팀의 구성원이 해체하면") {
			it("팀이 비활성화되어 활성 조회에서 사라진다 (200)") {
				val ownerId = 4001L
				val invitedUserId = 4002L
				val teamId: Long = formedTeam(ownerId, invitedUserId)

				delete("/teams/v1/$teamId") {
					bearer(accessTokenFor(invitedUserId))
				} expect {
					status(200)
					body("success", true)
				}

				allTeams().size shouldBe 0
			}
		}

		context("아직 INVITING인 팀에 해체를 호출하면") {
			it("409(TEAM-008)를 반환한다") {
				val ownerId = 4003L
				val invitedUserId = 4004L
				persistMatchUser(ownerId, Gender.MALE)
				persistMatchUser(invitedUserId, Gender.MALE)
				val teamId: Long = post("/teams/v1") {
					bearer(accessTokenFor(ownerId))
					jsonBody("""{"invitedUserId": $invitedUserId, "name": "우리팀", "introduction": null}""")
				}.extract().path<Int>("data.teamId").toLong()

				delete("/teams/v1/$teamId") {
					bearer(accessTokenFor(ownerId))
				} expect {
					status(409)
					body("error.code", "TEAM-008")
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
		IntegrationUtil.deleteAll(QTeamEntity.teamEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
	}
})

private fun allTeams(): List<TeamEntity> {
	val team: QTeamEntity = QTeamEntity.teamEntity
	return IntegrationUtil.getQuery().selectFrom(team).fetch()
}
```

- [ ] **Step 3: Run E2E to verify it fails**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.match.DisbandTeamE2ETest"`
Expected: 컴파일 실패/404.

- [ ] **Step 4: Implement the service**

Create `DisbandTeamService.kt`:

```kotlin
package com.org.oneulsogae.core.match.command.application

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.common.lock.DistributedLock
import com.org.oneulsogae.core.common.lock.LockKeyConstraints
import com.org.oneulsogae.core.match.TeamErrorCode
import com.org.oneulsogae.core.match.command.application.port.`in`.DisbandTeamUseCase
import com.org.oneulsogae.core.match.command.application.port.out.GetTeamPort
import com.org.oneulsogae.core.match.command.application.port.out.SaveTeamPort
import com.org.oneulsogae.core.match.command.domain.Team
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [DisbandTeamUseCase] 구현. FORMED 팀을 구성원이 해체한다.
 * 팀을 조회해 [Team.disband]로 비활성화(DEACTIVATED + soft delete)한 뒤 저장한다.
 * teamId 분산 락으로 동시 상태 변경과 직렬화한다.
 */
@Service
class DisbandTeamService(
	private val getTeamPort: GetTeamPort,
	private val saveTeamPort: SaveTeamPort,
) : DisbandTeamUseCase {

	@DistributedLock(prefix = LockKeyConstraints.TEAM_LIFECYCLE, keys = ["#teamId"], waitTime = 0)
	@Transactional
	override fun disband(userId: Long, teamId: Long): Team {
		val team: Team = getTeamPort.findById(teamId)
			?: throw BusinessException(TeamErrorCode.TEAM_NOT_FOUND)
		return saveTeamPort.save(team.disband(userId, LocalDateTime.now()))
	}
}
```

- [ ] **Step 5: Add the controller endpoint**

In `TeamController`, inject `DisbandTeamUseCase` and add:

```kotlin
	/** 결성(FORMED)된 팀을 구성원이 해체한다. (떠나면 2인 팀이 유지될 수 없어 팀 전체 비활성화) */
	@DeleteMapping("/{teamId}")
	fun disband(
		@LoginUser user: AuthUser,
		@PathVariable teamId: Long,
	): ApiResponse<TeamResponse> =
		ApiResponse.success(TeamResponse.of(disbandTeamUseCase.disband(user.id, teamId)))
```

- [ ] **Step 6: Run E2E to verify it passes**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.match.DisbandTeamE2ETest"`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/port/in/DisbandTeamUseCase.kt \
  oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/DisbandTeamService.kt \
  oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/match/TeamController.kt \
  oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/DisbandTeamE2ETest.kt
git commit -m "feat: 팀 해체·떠나기 엔드포인트(DELETE /teams/v1/{teamId})

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 8: InviteTeamService "한 팀만" 검증 + E2E

**Files:**
- Modify: `oneulsogae-core/.../match/command/application/InviteTeamService.kt`
- Test: `oneulsogae-api/src/test/.../api/match/InviteTeamE2ETest.kt`

**Interfaces:**
- Consumes: `GetTeamPort.existsActiveTeamMember` (Task 4), `TeamErrorCode.ALREADY_IN_TEAM` (Task 2)

- [ ] **Step 1: Write the failing E2E test**

Append two contexts inside `InviteTeamE2ETest`'s `describe("POST /teams/v1")` block:

```kotlin
			context("초대자(owner)가 이미 활성 팀에 속해 있으면") {
				it("409(TEAM-009)을 반환한다") {
					val ownerId = 1001L
					val invited1 = 1002L
					val invited2 = 1003L
					persistMatchUser(ownerId, Gender.MALE)
					persistMatchUser(invited1, Gender.MALE)
					persistMatchUser(invited2, Gender.MALE)

					// 첫 팀 결성(owner는 활성 구성원이 됨)
					post("/teams/v1") {
						bearer(accessTokenFor(ownerId))
						jsonBody("""{"invitedUserId": $invited1, "name": "팀1", "introduction": null}""")
					} expect { status(200) }

					// 같은 owner가 또 초대 → 한 팀만 위반
					post("/teams/v1") {
						bearer(accessTokenFor(ownerId))
						jsonBody("""{"invitedUserId": $invited2, "name": "팀2", "introduction": null}""")
					} expect {
						status(409)
						body("error.code", "TEAM-009")
					}
				}
			}

			context("초대 대상이 이미 활성 팀에 속해 있으면") {
				it("409(TEAM-009)을 반환한다") {
					val owner1 = 1001L
					val owner2 = 1004L
					val invitedUserId = 1002L
					persistMatchUser(owner1, Gender.MALE)
					persistMatchUser(owner2, Gender.MALE)
					persistMatchUser(invitedUserId, Gender.MALE)

					// invitedUserId가 owner1 팀의 구성원이 됨
					post("/teams/v1") {
						bearer(accessTokenFor(owner1))
						jsonBody("""{"invitedUserId": $invitedUserId, "name": "팀1", "introduction": null}""")
					} expect { status(200) }

					// owner2가 같은 사람을 초대 → 한 팀만 위반
					post("/teams/v1") {
						bearer(accessTokenFor(owner2))
						jsonBody("""{"invitedUserId": $invitedUserId, "name": "팀2", "introduction": null}""")
					} expect {
						status(409)
						body("error.code", "TEAM-009")
					}
				}
			}
```

- [ ] **Step 2: Run E2E to verify it fails**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.match.InviteTeamE2ETest"`
Expected: 두 신규 컨텍스트 FAIL — 둘째 초대가 200으로 통과(검증 부재).

- [ ] **Step 3: Add the "한 팀만" check to InviteTeamService**

Modify `InviteTeamService`: inject `GetTeamPort`, add `TeamErrorCode` import, and guard before building the team:

```kotlin
@Service
class InviteTeamService(
	private val getMatchUserPort: GetMatchUserPort,
	private val getTeamPort: GetTeamPort,
	private val saveTeamPort: SaveTeamPort,
) : InviteTeamUseCase {

	@Transactional
	override fun invite(ownerId: Long, command: InviteTeamCommand): Team {
		// 한 사용자는 활성 팀(INVITING/FORMED)에 하나만 속할 수 있다. owner·초대대상 모두 검증.
		validateNotInActiveTeam(ownerId)
		validateNotInActiveTeam(command.invitedUserId)

		val ownerGender: Gender = genderOf(ownerId)
		val invitedGender: Gender = genderOf(command.invitedUserId)

		val team: Team = Team.invite(
			ownerId = ownerId,
			ownerGender = ownerGender,
			invitedUserId = command.invitedUserId,
			invitedGender = invitedGender,
			name = command.name,
			introduction = command.introduction,
		)
		return saveTeamPort.save(team)
	}

	// 이미 활성 팀 구성원이면 ALREADY_IN_TEAM.
	private fun validateNotInActiveTeam(userId: Long) {
		if (getTeamPort.existsActiveTeamMember(userId)) {
			throw BusinessException(TeamErrorCode.ALREADY_IN_TEAM)
		}
	}

	// 매칭 읽기 모델(match_user)에서 성별을 읽는다. 행이 없으면 매칭 가능 상태가 아니므로 예외.
	private fun genderOf(userId: Long): Gender =
		getMatchUserPort.findByUserId(userId)?.gender
			?: throw BusinessException(MatchErrorCode.PROFILE_INCOMPLETE)
}
```

Add imports: `import com.org.oneulsogae.core.match.TeamErrorCode` and `import com.org.oneulsogae.core.match.command.application.port.out.GetTeamPort`.

- [ ] **Step 4: Run E2E to verify it passes**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.match.InviteTeamE2ETest"`
Expected: PASS (기존 케이스 포함 전부)

- [ ] **Step 5: Commit**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/InviteTeamService.kt \
  oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/InviteTeamE2ETest.kt
git commit -m "feat: 팀 초대 시 '한 팀만' 제약 검증(ALREADY_IN_TEAM) 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 9: 전체 회귀 + 마무리

**Files:** 없음 (검증)

- [ ] **Step 1: Run the full oneulsogae-api test suite**

Run: `./gradlew :oneulsogae-api:test`
Expected: BUILD SUCCESSFUL — 도메인 유닛(TeamTest/TeamMembersTest) + E2E 4종(Invite/Accept/Withdraw/Disband) 전부 그린.

- [ ] **Step 2: Run the full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3 (해당 시): 메모리 갱신 안내**

`team-matching-aggregate` 메모리의 "초대=즉시 합류(auto-join)" 서술은 현재 코드(수락해야 합류)와 어긋난다. 구현 완료 후 메인 에이전트가 메모리를 갱신한다(이 플랜 범위 밖, 별도 처리).

---

## Self-Review

**1. Spec coverage**
- 상태 모델(INVITING→FORMED / DEACTIVATED+soft delete) → Task 2·3 도메인 + Task 5~7 서비스. ✅
- API 3종(acceptance / invitation / {teamId}) → Task 5·6·7. ✅
- 기존 invite "한 팀만" 추가 → Task 8. ✅
- 도메인 행위(accept/withdraw/disband, TeamMembers) → Task 1·2·3. ✅
- GetTeamPort + TeamAdapter + existsByUserId → Task 4. ✅
- 동시성 락(TEAM_LIFECYCLE, teamId, waitTime=0) → Task 5(상수) + 5·6·7 서비스. ✅
- 에러코드 TEAM-005~009 → Task 2. ✅
- 테스트(도메인 유닛 + E2E) → 각 태스크 + Task 9 회귀. ✅
- 범위 밖(조회·페어링·코인·채팅) → 어떤 태스크도 건드리지 않음. ✅

**2. Placeholder scan**: 모든 스텝에 실제 코드·명령·기대 출력 포함. TODO/TBD 없음. ✅

**3. Type consistency**:
- `accept(userId, teamId)` / `withdraw(userId, teamId)` / `disband(userId, teamId)` — 서비스·유스케이스·컨트롤러 시그니처 일치. ✅
- `GetTeamPort.findById`/`existsActiveTeamMember` — Task 4 정의, Task 5~8 소비 일치. ✅
- `Team.acceptInvitation(userId)` / `withdrawInvitation(userId, now)` / `disband(userId, now)` — Task 2·3 정의, Task 5~7 호출 일치. ✅
- `TeamMembers.find/accept/allActive/deactivateAll` — Task 1 정의, Task 2·3 소비 일치. ✅
- 락 상수 `LockKeyConstraints.TEAM_LIFECYCLE` — Task 5 정의, Task 5·6·7 사용 일치. ✅
