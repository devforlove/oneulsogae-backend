# 팀 매칭 관심 요청·수락 API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 팀 매칭(`TeamMatch`)에서 한 팀이 상대 팀에 관심을 보내고 양 팀이 신청하면 성사되는 단일 엔드포인트 API를, 1:1 매칭 `SendInterestService`를 미러링해 추가한다.

**Architecture:** 헥사고날(Ports & Adapters) + CQRS. 도메인(`TeamMatch`/`MatchedTeams`)에 상태 전이 메서드를 추가하고, command 서비스가 결과 상태로 신청(코인 차감+알림)·성사(코인 차감+4인 채팅방+알림)를 분기한다. 알림은 커밋 후 best-effort 이벤트 핸들러로 처리한다.

**Tech Stack:** Kotlin 2.2.21 / JVM 21, Spring Boot 4, Spring Data JPA, QueryDSL, Kotest(도메인 유닛), Testcontainers(api E2E).

## Global Constraints

- 변수·반환 타입·람다 파라미터 타입을 명시한다(표현식 본문 함수 포함).
- 현재 시각은 `LocalDateTime.now()` 직접 호출 금지. 서비스는 `TimeGenerator.now()` 주입, 도메인엔 파라미터로 넘긴다.
- 다른 도메인(coin/chat/user)은 in-port `UseCase`로만 참조한다. 자기 도메인 영속성은 자기 out-port로 접근한다.
- 조회/명령 분리: 명령 서비스 `@Transactional`, 조회 `@Transactional(readOnly = true)`.
- 컨트롤러는 Service가 아니라 in-port `UseCase`를 주입한다.
- 도메인 검증은 도메인 모델 메서드(`validate…`)로 캡슐화한다.
- 에러는 도메인별 `ErrorCode` enum + `BusinessException`.
- 코인 금액 상수: `CoinUsageType.MEETING_INIT.coinAmount = 40`, `CoinUsageType.MEETING_ACCEPT.coinAmount = 40`.
- 도메인 유닛 테스트 위치: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/match/`. api E2E: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/` (`AbstractIntegrationSupport` 상속).

---

### Task 1: 도메인 — `MatchedTeam.hasApplied` + `MatchedTeams` 신청/집계 메서드

기존 1:1 `MatchMembers`(`apply`/`allApplied`/`anyApplied`/`activateAll`/`isParticipant`)를 팀 참가 컬렉션에 대응시킨다.

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/MatchedTeam.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/MatchedTeams.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/match/MatchedTeamsTest.kt` (기존 파일에 추가)

**Interfaces:**
- Produces:
  - `MatchedTeam.hasApplied: Boolean` (status가 APPLY 또는 ACTIVE)
  - `MatchedTeams.apply(teamId: Long): MatchedTeams`
  - `MatchedTeams.allApplied(): Boolean`
  - `MatchedTeams.anyApplied(): Boolean`
  - `MatchedTeams.activateAll(): MatchedTeams`
  - `MatchedTeams.isParticipant(teamId: Long): Boolean`

- [ ] **Step 1: 실패하는 테스트 작성** (`MatchedTeamsTest.kt`의 `DescribeSpec` 블록 안, 기존 `describe("deactivateAll")` 아래에 추가)

```kotlin
	describe("apply - 특정 팀 신청") {
		it("주어진 teamId 팀만 APPLY로 전이하고 나머지는 그대로다") {
			val matchedTeams: MatchedTeams = MatchedTeams.of(listOf(10L, 20L))

			val applied: MatchedTeams = matchedTeams.apply(10L)

			applied.values.first { it.teamId == 10L }.status shouldBe MatchedTeamStatus.APPLY
			applied.values.first { it.teamId == 20L }.status shouldBe MatchedTeamStatus.WAITING
			// 원본 불변
			matchedTeams.values.all { it.status == MatchedTeamStatus.WAITING } shouldBe true
		}
	}

	describe("allApplied / anyApplied") {
		it("전원 신청이면 allApplied=true, 일부면 anyApplied만 true") {
			val none: MatchedTeams = MatchedTeams.of(listOf(10L, 20L))
			none.anyApplied() shouldBe false
			none.allApplied() shouldBe false

			val partial: MatchedTeams = none.apply(10L)
			partial.anyApplied() shouldBe true
			partial.allApplied() shouldBe false

			val all: MatchedTeams = partial.apply(20L)
			all.allApplied() shouldBe true
		}
	}

	describe("activateAll") {
		it("모든 참가 팀을 ACTIVE로 승격한다") {
			val matchedTeams: MatchedTeams = MatchedTeams.of(listOf(10L, 20L))

			val activated: MatchedTeams = matchedTeams.activateAll()

			activated.values.all { it.status == MatchedTeamStatus.ACTIVE } shouldBe true
		}
	}

	describe("isParticipant") {
		it("teamId가 참가 팀이면 true, 아니면 false") {
			val matchedTeams: MatchedTeams = MatchedTeams.of(listOf(10L, 20L))

			matchedTeams.isParticipant(10L) shouldBe true
			matchedTeams.isParticipant(99L) shouldBe false
		}
	}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.match.MatchedTeamsTest"`
Expected: 컴파일 실패 또는 FAIL — `apply`/`allApplied`/`anyApplied`/`activateAll`/`isParticipant` 미정의.

- [ ] **Step 3: `MatchedTeam`에 `hasApplied` 추가** (`MatchedTeam.kt`, `deactivate()` 메서드 아래)

```kotlin
	/** 이 팀이 신청(또는 성사로 활성)했는지 여부. (APPLY/ACTIVE) */
	val hasApplied: Boolean
		get() = status == MatchedTeamStatus.APPLY || status == MatchedTeamStatus.ACTIVE
```

- [ ] **Step 4: `MatchedTeams`에 메서드 추가** (`MatchedTeams.kt`, `deactivateAll()` 아래)

```kotlin
	/** [teamId]가 이 팀 매칭의 참가 팀인지 여부. */
	fun isParticipant(teamId: Long): Boolean =
		values.any { matchedTeam: MatchedTeam -> matchedTeam.teamId == teamId }

	/** [teamId] 팀을 신청(APPLY) 처리한 새 컬렉션을 반환한다. (나머지는 그대로) */
	fun apply(teamId: Long): MatchedTeams =
		MatchedTeams(values.map { matchedTeam: MatchedTeam -> if (matchedTeam.teamId == teamId) matchedTeam.apply() else matchedTeam })

	/** 모든 참가 팀이 신청했는지 여부. (참가 팀이 있고 전원 APPLY/ACTIVE) */
	fun allApplied(): Boolean =
		values.isNotEmpty() && values.all { matchedTeam: MatchedTeam -> matchedTeam.hasApplied }

	/** 한 팀이라도 신청했는지 여부. */
	fun anyApplied(): Boolean =
		values.any { matchedTeam: MatchedTeam -> matchedTeam.hasApplied }

	/** 모든 참가 팀을 활성(ACTIVE)으로 승격한 새 컬렉션을 반환한다. (양 팀 신청으로 성사 시) */
	fun activateAll(): MatchedTeams =
		MatchedTeams(values.map { matchedTeam: MatchedTeam -> matchedTeam.activate() })
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.match.MatchedTeamsTest"`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/MatchedTeam.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/MatchedTeams.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/match/MatchedTeamsTest.kt
git commit -m "feat(match): MatchedTeams 신청·집계 메서드 추가 (apply/allApplied/anyApplied/activateAll/isParticipant)"
```

---

### Task 2: 도메인 — `TeamMatch.respond`/`validateRespondable` + `TeamMatchErrorCode`

1:1 `Match.respond`/`validateRespondable`를 미러링한다. 전원 APPLY면 MATCHED + 전원 ACTIVE + 만료 100년 연장.

**Files:**
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/TeamMatchErrorCode.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/TeamMatch.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/match/TeamMatchTest.kt` (기존 파일에 추가)

**Interfaces:**
- Consumes (Task 1): `MatchedTeams.apply/allApplied/anyApplied/activateAll/isParticipant`.
- Produces:
  - `TeamMatchErrorCode { TEAM_MATCH_NOT_FOUND, NOT_TEAM_MATCH_PARTICIPANT, TEAM_MATCH_ALREADY_CLOSED }`
  - `TeamMatch.respond(teamId: Long): TeamMatch`
  - `TeamMatch.validateRespondable(teamId: Long)`
  - `TeamMatch.isParticipant(teamId: Long): Boolean`

- [ ] **Step 1: `TeamMatchErrorCode` 생성** (`TeamMatchErrorCode.kt`)

```kotlin
package com.org.oneulsogae.core.match

import com.org.oneulsogae.core.common.error.ErrorCode
import org.springframework.http.HttpStatus

/**
 * 팀 매칭(TeamMatch) 도메인 에러 코드.
 * [com.org.oneulsogae.core.common.error.BusinessException]에 넘겨 사용한다.
 */
enum class TeamMatchErrorCode(
	override val code: String,
	override val message: String,
	override val status: HttpStatus,
) : ErrorCode {

	TEAM_MATCH_NOT_FOUND("TEAM-MATCH-001", "팀 매칭을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
	NOT_TEAM_MATCH_PARTICIPANT("TEAM-MATCH-002", "해당 팀 매칭의 참가 팀 구성원이 아닙니다.", HttpStatus.FORBIDDEN),
	TEAM_MATCH_ALREADY_CLOSED("TEAM-MATCH-003", "이미 종료된 팀 매칭입니다.", HttpStatus.CONFLICT),
}
```

- [ ] **Step 2: 실패하는 테스트 작성** (`TeamMatchTest.kt`의 `DescribeSpec` 블록 안, 기존 `describe("isMatched")` 아래에 추가. 상단 import에 `import com.org.oneulsogae.core.common.error.BusinessException`, `import com.org.oneulsogae.core.match.TeamMatchErrorCode`, `import io.kotest.assertions.throwables.shouldThrow`, `import io.kotest.matchers.shouldNotBe` 추가)

```kotlin
	describe("respond - 팀 관심 신청/성사") {
		it("한 팀만 신청하면 그 팀이 APPLY, 매칭은 PARTIALLY_ACCEPTED가 된다") {
			val teamMatch: TeamMatch = TeamMatch.propose(10L, 20L, TeamMatchType.RECOMMENDED, now)

			val responded: TeamMatch = teamMatch.respond(10L)

			responded.status shouldBe MatchStatus.PARTIALLY_ACCEPTED
			responded.matchedTeams.values.first { it.teamId == 10L }.status shouldBe MatchedTeamStatus.APPLY
			responded.matchedTeams.values.first { it.teamId == 20L }.status shouldBe MatchedTeamStatus.WAITING
			// 미성사는 만료 시각 그대로
			responded.expiresAt shouldBe now.plusDays(1)
		}

		it("양 팀이 모두 신청하면 MATCHED + 전원 ACTIVE가 되고 만료가 100년 연장된다") {
			val teamMatch: TeamMatch = TeamMatch.propose(10L, 20L, TeamMatchType.RECOMMENDED, now)

			val matched: TeamMatch = teamMatch.respond(10L).respond(20L)

			matched.status shouldBe MatchStatus.MATCHED
			matched.matchedTeams.values.all { it.status == MatchedTeamStatus.ACTIVE } shouldBe true
			matched.expiresAt shouldBe now.plusDays(1).plusYears(100)
		}
	}

	describe("validateRespondable") {
		it("참가 팀이 아니면 NOT_TEAM_MATCH_PARTICIPANT를 던진다") {
			val teamMatch: TeamMatch = TeamMatch.propose(10L, 20L, TeamMatchType.RECOMMENDED, now)

			val ex: BusinessException = shouldThrow { teamMatch.validateRespondable(99L) }
			ex.errorCode shouldBe TeamMatchErrorCode.NOT_TEAM_MATCH_PARTICIPANT
		}

		it("이미 종료(CLOSED)된 매칭이면 TEAM_MATCH_ALREADY_CLOSED를 던진다") {
			val closed: TeamMatch = TeamMatch.propose(10L, 20L, TeamMatchType.RECOMMENDED, now).close()

			val ex: BusinessException = shouldThrow { closed.validateRespondable(10L) }
			ex.errorCode shouldBe TeamMatchErrorCode.TEAM_MATCH_ALREADY_CLOSED
		}
	}
```

> 참고: `BusinessException`의 에러코드 프로퍼티명이 `errorCode`가 맞는지 `oneulsogae-core/.../common/error/BusinessException.kt`에서 확인하고, 다르면 단언 프로퍼티명을 맞춘다.

- [ ] **Step 3: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.match.TeamMatchTest"`
Expected: 컴파일 실패 — `respond`/`validateRespondable` 미정의.

- [ ] **Step 4: `TeamMatch`에 메서드 추가** (`TeamMatch.kt`)

상단 import에 추가:
```kotlin
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.match.TeamMatchErrorCode
```

`isMatched()` 메서드 아래에 추가:
```kotlin
	/** [teamId]가 이 팀 매칭의 참가 팀인지 여부. */
	fun isParticipant(teamId: Long): Boolean =
		matchedTeams.isParticipant(teamId)

	/**
	 * 해당 팀이 이 팀 매칭에 관심(신청)을 보낼 수 있는 상태인지 검증한다.
	 * 참가 팀이 아니면 [TeamMatchErrorCode.NOT_TEAM_MATCH_PARTICIPANT], 이미 종료된 매칭이면 [TeamMatchErrorCode.TEAM_MATCH_ALREADY_CLOSED]를 던진다.
	 */
	fun validateRespondable(teamId: Long) {
		if (!isParticipant(teamId)) {
			throw BusinessException(TeamMatchErrorCode.NOT_TEAM_MATCH_PARTICIPANT)
		}
		if (status.isClosed()) {
			throw BusinessException(TeamMatchErrorCode.TEAM_MATCH_ALREADY_CLOSED)
		}
	}

	/**
	 * 참가 팀의 관심 신청을 반영한 새 상태를 만든다. (참가/미종료 검증은 호출 측 책임)
	 * 응답 팀을 APPLY로 바꾸고, 전원 신청이면 MATCHED로 만들며 전원을 ACTIVE로 승격한다. 일부만 신청이면 PARTIALLY_ACCEPTED.
	 * 성사(MATCHED)되면 만료로 목록에서 사라지지 않게 만료 시각을 100년 뒤로 미룬다. (1:1 매칭과 동일)
	 */
	fun respond(teamId: Long): TeamMatch {
		val applied: TeamMatch = copy(matchedTeams = matchedTeams.apply(teamId))
		val recomputed: TeamMatch = applied.withRecomputedStatus()
		return if (recomputed.status == MatchStatus.MATCHED) recomputed.extendExpirationForMatched() else recomputed
	}

	private fun withRecomputedStatus(): TeamMatch =
		when {
			matchedTeams.allApplied() -> copy(status = MatchStatus.MATCHED, matchedTeams = matchedTeams.activateAll())
			matchedTeams.anyApplied() -> copy(status = MatchStatus.PARTIALLY_ACCEPTED)
			else -> copy(status = MatchStatus.PROPOSED)
		}

	// 성사된 매칭의 만료 시각을 [MATCHED_EXPIRATION_EXTENSION_YEARS]년 뒤로 미룬다. (성사 후엔 새 소개를 안 해 사실상 만료 없음)
	private fun extendExpirationForMatched(): TeamMatch =
		copy(expiresAt = expiresAt.plusYears(MATCHED_EXPIRATION_EXTENSION_YEARS))
```

`companion object` 안 `EXPIRATION` 아래에 상수 추가:
```kotlin
		/** 성사 매칭의 만료 연장 연수. 성사 후엔 새 소개를 안 해, 사실상 만료되지 않도록 100년을 더한다. (1:1 [Match]와 동일) */
		const val MATCHED_EXPIRATION_EXTENSION_YEARS: Long = 100L
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.match.TeamMatchTest"`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/TeamMatchErrorCode.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/TeamMatch.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/match/TeamMatchTest.kt
git commit -m "feat(match): TeamMatch 관심 신청·성사 전이(respond)와 TeamMatchErrorCode 추가"
```

---

### Task 3: 아웃포트 — `GetTeamMatchPort.findById` + 어댑터 구현

서비스가 teamMatchId로 단건 로드할 수 있도록 조회 포트를 확장한다. (현재 `findActiveByTeamId`만 존재)

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/port/out/GetTeamMatchPort.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/adapter/TeamMatchAdapter.kt`

**Interfaces:**
- Produces: `GetTeamMatchPort.findById(teamMatchId: Long): TeamMatch?` (소프트 삭제 제외, CLOSED 포함)

- [ ] **Step 1: 포트에 메서드 선언 추가** (`GetTeamMatchPort.kt`, `findActiveByTeamId` 아래)

```kotlin
	/** 팀 매칭 애그리거트(헤더 + 참가 팀)를 id로 조회한다. 없으면 null. (소프트 삭제 제외, 종료(CLOSED) 매칭도 포함) */
	fun findById(teamMatchId: Long): TeamMatch?
```

- [ ] **Step 2: 어댑터에 구현 추가** (`TeamMatchAdapter.kt`, `save` 아래)

```kotlin
	override fun findById(teamMatchId: Long): TeamMatch? {
		val header: TeamMatchEntity = teamMatchJpaRepository.findById(teamMatchId).orElse(null) ?: return null
		val matchedTeams: MatchedTeams = MatchedTeams(
			matchedTeamJpaRepository.findByTeamMatchIdIn(listOf(teamMatchId)).map { it.toDomain() },
		)
		return header.toDomain(matchedTeams)
	}
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :oneulsogae-infra:compileKotlin`
Expected: BUILD SUCCESSFUL (동작은 Task 7 E2E에서 검증)

- [ ] **Step 4: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/port/out/GetTeamMatchPort.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/adapter/TeamMatchAdapter.kt
git commit -m "feat(match): GetTeamMatchPort.findById 추가 및 TeamMatchAdapter 구현"
```

---

### Task 4: 이벤트 + 락 상수 + 인포트 — 팀 관심/성사 이벤트, 락, UseCase

서비스/핸들러가 의존할 계약을 먼저 만든다.

**Files:**
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/event/TeamMatchInterestSent.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/event/TeamMatchAccepted.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/port/in/SendTeamInterestUseCase.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/common/lock/LockKeyConstraints.kt`

**Interfaces:**
- Produces:
  - `TeamMatchInterestSent(teamMatchId: Long, senderTeamId: Long, recipientUserIds: List<Long>)`
  - `TeamMatchAccepted(teamMatchId: Long, recipientUserIds: List<Long>)`
  - `SendTeamInterestUseCase.sendInterest(userId: Long, teamMatchId: Long): TeamMatch`
  - `LockKeyConstraints.TEAM_MATCH_INTEREST: String`

- [ ] **Step 1: 관심 신청 이벤트 생성** (`TeamMatchInterestSent.kt`)

```kotlin
package com.org.oneulsogae.core.match.command.domain.event

/**
 * 팀 매칭에 한 팀이 관심(신청)을 보냈을 때 발행되는 도메인 이벤트.
 * 커밋 이후 알림 처리에 필요한 식별 정보만 담는다.
 * [recipientUserIds]는 알림 수신자(상대 팀의 ACTIVE 구성원), [senderTeamId]는 관심을 보낸 팀이다.
 */
data class TeamMatchInterestSent(
	val teamMatchId: Long,
	val senderTeamId: Long,
	val recipientUserIds: List<Long>,
)
```

- [ ] **Step 2: 성사 이벤트 생성** (`TeamMatchAccepted.kt`)

```kotlin
package com.org.oneulsogae.core.match.command.domain.event

/**
 * 팀 매칭이 양 팀 신청으로 성사(MATCHED)됐을 때 발행되는 도메인 이벤트.
 * [recipientUserIds]는 알림 수신자(양 팀 4인 중 마지막에 신청해 성사를 만든 본인을 제외한 구성원)다.
 */
data class TeamMatchAccepted(
	val teamMatchId: Long,
	val recipientUserIds: List<Long>,
)
```

- [ ] **Step 3: UseCase 인포트 생성** (`SendTeamInterestUseCase.kt`)

```kotlin
package com.org.oneulsogae.core.match.command.application.port.`in`

import com.org.oneulsogae.core.match.command.domain.TeamMatch

/**
 * 팀 매칭 관심 보내기 인포트(유스케이스). 신청과 수락을 하나로 다룬다.
 * 참가 팀의 ACTIVE 구성원이 팀을 대표해 관심을 보낸다. 상대 팀이 아직 신청 안 했으면 신청(→ PARTIALLY_ACCEPTED),
 * 이미 신청했으면 수락이 되어 성사([com.org.oneulsogae.common.match.MatchStatus.MATCHED])된다.
 * 차감 코인(신청/수락 비용)은 팀 매칭 상태로 서버가 산출하며, 행위한 구성원이 부담한다.
 */
interface SendTeamInterestUseCase {

	fun sendInterest(userId: Long, teamMatchId: Long): TeamMatch
}
```

- [ ] **Step 4: 락 상수 추가** (`LockKeyConstraints.kt`, `TEAM_LIFECYCLE` 아래)

```kotlin

	/**
	 * 팀 매칭 관심(신청/수락) 처리 락. teamMatchId로 잠가 같은 팀 매칭의 상태 변경을 직렬화한다.
	 * 두 팀이 공유하는 "팀 매칭"이 경합 대상이므로 userId/teamId가 아니라 teamMatchId로 잠근다.
	 * 동시 요청·더블클릭으로 인한 lost update·코인 이중 차감을 막는다. (waitTime=0이면 겹친 요청은 즉시 실패)
	 */
	const val TEAM_MATCH_INTEREST: String = "TEAM_MATCH_INTEREST"
```

- [ ] **Step 5: 컴파일 확인**

Run: `./gradlew :oneulsogae-core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/event/TeamMatchInterestSent.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/event/TeamMatchAccepted.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/port/in/SendTeamInterestUseCase.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/common/lock/LockKeyConstraints.kt
git commit -m "feat(match): 팀 매칭 관심·성사 이벤트·UseCase 인포트·락 상수 추가"
```

---

### Task 5: 서비스 — `SendTeamInterestService`

1:1 `SendInterestService`를 미러링. 행위자 팀 식별·검증 → `respond` → 결과 상태로 신청/성사 분기.

**Files:**
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/SendTeamInterestService.kt`

**Interfaces:**
- Consumes:
  - `GetTeamMatchPort.findById` (Task 3), `SaveTeamMatchPort.save`
  - `GetTeamPort.findById` (`com.org.oneulsogae.core.match.command.application.port.out.GetTeamPort`)
  - `Team.activeMemberIds(): List<Long>`, `Team.id`, `TeamMatch.respond/validateRespondable/teamIds`
  - `SpendCoinUseCase.spend(userId, SpendCoinCommand(amount, coinUsageType))`
  - `SaveChatRoomUseCase.save(SaveChatRoomCommand(matchId, participantUserIds))`
  - `DomainEventPublisher.publish(event)`
  - 이벤트: `TeamMatchInterestSent`, `TeamMatchAccepted` (Task 4)
  - `TeamMatchErrorCode` (Task 2), `LockKeyConstraints.TEAM_MATCH_INTEREST` (Task 4)
- Produces: `SendTeamInterestService : SendTeamInterestUseCase`

> 참고: `Team`은 `matchedTeams`가 아니라 `members: TeamMembers`를 가지며 `activeMemberIds()`는 `Team`에 위임 메서드가 있는지 확인한다. `Team.kt`를 열어 `fun activeMemberIds(): List<Long>`가 있으면 사용하고, 없으면 `team.members.activeMemberIds()`를 쓴다. (탐색 결과 `DisbandTeamService`가 `team.activeMemberIds()`를 사용하므로 `Team`에 존재함)

- [ ] **Step 1: 서비스 생성** (`SendTeamInterestService.kt`)

```kotlin
package com.org.oneulsogae.core.match.command.application

import com.org.oneulsogae.common.coin.CoinUsageType
import com.org.oneulsogae.common.match.MatchStatus
import com.org.oneulsogae.core.chat.command.application.port.`in`.SaveChatRoomUseCase
import com.org.oneulsogae.core.chat.command.application.port.`in`.command.SaveChatRoomCommand
import com.org.oneulsogae.core.coin.command.application.port.`in`.SpendCoinUseCase
import com.org.oneulsogae.core.coin.command.application.port.`in`.command.SpendCoinCommand
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.common.event.DomainEventPublisher
import com.org.oneulsogae.core.common.lock.DistributedLock
import com.org.oneulsogae.core.common.lock.LockKeyConstraints
import com.org.oneulsogae.core.match.TeamMatchErrorCode
import com.org.oneulsogae.core.match.command.application.port.`in`.SendTeamInterestUseCase
import com.org.oneulsogae.core.match.command.application.port.out.GetTeamMatchPort
import com.org.oneulsogae.core.match.command.application.port.out.GetTeamPort
import com.org.oneulsogae.core.match.command.application.port.out.SaveTeamMatchPort
import com.org.oneulsogae.core.match.command.domain.Team
import com.org.oneulsogae.core.match.command.domain.TeamMatch
import com.org.oneulsogae.core.match.command.domain.event.TeamMatchAccepted
import com.org.oneulsogae.core.match.command.domain.event.TeamMatchInterestSent
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [SendTeamInterestUseCase] 구현. 팀 매칭의 신청과 수락을 하나로 처리한다. (1:1 [SendInterestService] 미러)
 * 팀 매칭·참가 두 팀을 로드해 행위자가 속한 ACTIVE 팀을 식별하고, 미종료를 검증한 뒤 관심을 반영한다.
 * 결과 매칭 상태로 후속 처리를 나눈다:
 * - 성사(MATCHED): 수락 비용 차감 + 4인 채팅방 생성(동기) + 성사 알림 위임. ([completeMatch])
 * - 미성사(PARTIALLY_ACCEPTED): 신청 비용 차감 + 상대 팀에 관심 받음 알림 위임. ([recordInterest])
 *
 * 코인 차감·상태 변경·채팅방 생성은 같은 트랜잭션이라 한 단계라도 실패하면 함께 롤백된다. 알림만 커밋 후 best-effort([TeamMatchEventHandler])다.
 * 채팅방은 성사의 필수 산출물이라 같은 트랜잭션에서 동기로 만든다. matchId로는 teamMatch.id를 쓴다. (기존 [DisbandTeamService] 컨벤션과 동일)
 * 다른 도메인(coin/chat)은 자기 out-port가 아니라 in-port로 참조한다.
 *
 * 팀 매칭별 분산 락([DistributedLock], "TEAM_MATCH_INTEREST::{teamMatchId}")으로 보호한다. 경합 대상이 두 팀이 공유하는
 * "팀 매칭"이므로 teamMatchId로 잠근다. waitTime=0이라 같은 매칭에 동시 요청이 겹치면 한쪽은 즉시 실패(409)한다.
 */
@Service
class SendTeamInterestService(
	private val getTeamMatchPort: GetTeamMatchPort,
	private val saveTeamMatchPort: SaveTeamMatchPort,
	private val getTeamPort: GetTeamPort,
	private val spendCoinUseCase: SpendCoinUseCase,
	private val saveChatRoomUseCase: SaveChatRoomUseCase,
	private val domainEventPublisher: DomainEventPublisher,
) : SendTeamInterestUseCase {

	@DistributedLock(prefix = LockKeyConstraints.TEAM_MATCH_INTEREST, keys = ["#teamMatchId"], waitTime = 0)
	@Transactional
	override fun sendInterest(userId: Long, teamMatchId: Long): TeamMatch {
		val teamMatch: TeamMatch = getTeamMatchPort.findById(teamMatchId)
			?: throw BusinessException(TeamMatchErrorCode.TEAM_MATCH_NOT_FOUND)

		// 참가 두 팀을 로드해 행위자가 ACTIVE 구성원으로 속한 팀을 식별한다. (참가 검증 겸함)
		val teams: List<Team> = teamMatch.matchedTeams.teamIds().mapNotNull { teamId: Long -> getTeamPort.findById(teamId) }
		val actorTeam: Team = teams.firstOrNull { team: Team -> userId in team.activeMemberIds() }
			?: throw BusinessException(TeamMatchErrorCode.NOT_TEAM_MATCH_PARTICIPANT)

		teamMatch.validateRespondable(actorTeam.id)

		val updated: TeamMatch = saveTeamMatchPort.save(teamMatch.respond(actorTeam.id))
		return when (updated.status) {
			MatchStatus.MATCHED -> completeMatch(userId, updated, teams)
			MatchStatus.PARTIALLY_ACCEPTED -> recordInterest(userId, updated, actorTeam, teams)
			else -> error("팀 관심 보내기 결과 상태가 올바르지 않습니다: ${updated.status}")
		}
	}

	/** 성사된 경우: 수락 비용 차감 + 4인 채팅방 생성(동기) + 성사 알림 위임(행위자 제외 양 팀 구성원). */
	private fun completeMatch(userId: Long, teamMatch: TeamMatch, teams: List<Team>): TeamMatch {
		spend(userId, amount = teamMatch.dateAcceptAmount, usageType = CoinUsageType.MEETING_ACCEPT)
		val allMemberIds: List<Long> = teams.flatMap { team: Team -> team.activeMemberIds() }
		saveChatRoomUseCase.save(
			SaveChatRoomCommand(matchId = teamMatch.id, participantUserIds = allMemberIds),
		)
		domainEventPublisher.publish(
			TeamMatchAccepted(teamMatchId = teamMatch.id, recipientUserIds = allMemberIds.filter { it != userId }),
		)
		return teamMatch
	}

	/** 미성사(신청)인 경우: 신청 비용 차감 + 상대 팀 구성원에게 관심 받음 알림 위임. */
	private fun recordInterest(userId: Long, teamMatch: TeamMatch, actorTeam: Team, teams: List<Team>): TeamMatch {
		spend(userId, amount = teamMatch.dateInitAmount, usageType = CoinUsageType.MEETING_INIT)
		val opponentMemberIds: List<Long> = teams.filter { team: Team -> team.id != actorTeam.id }
			.flatMap { team: Team -> team.activeMemberIds() }
		domainEventPublisher.publish(
			TeamMatchInterestSent(teamMatchId = teamMatch.id, senderTeamId = actorTeam.id, recipientUserIds = opponentMemberIds),
		)
		return teamMatch
	}

	/** 팀 매칭에 저장된 금액·유형으로 코인을 차감한다. (같은 트랜잭션이라 이후 실패 시 함께 롤백) */
	private fun spend(userId: Long, amount: Int, usageType: CoinUsageType) {
		spendCoinUseCase.spend(userId, SpendCoinCommand(amount = amount, coinUsageType = usageType))
	}
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :oneulsogae-core:compileKotlin`
Expected: BUILD SUCCESSFUL. (실패 시 `Team.activeMemberIds()` 시그니처/`MatchedTeams.teamIds()` 접근을 `Team.kt`/`MatchedTeams.kt`로 확인해 수정)

- [ ] **Step 3: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/SendTeamInterestService.kt
git commit -m "feat(match): 팀 매칭 관심 신청·수락 SendTeamInterestService 추가"
```

---

### Task 6: 이벤트 핸들러 — `TeamMatchEventHandler`

커밋 후 best-effort로 알림을 저장한다. AlarmType은 기존 `MANY_TO_MANY_*`를 재사용한다.

**Files:**
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/TeamMatchEventHandler.kt`

**Interfaces:**
- Consumes: `TeamMatchInterestSent`, `TeamMatchAccepted` (Task 4), `SaveAlarmUseCase.save(SaveAlarmCommand(...))`, `AlarmType.MANY_TO_MANY_INTEREST_RECEIVED`/`MANY_TO_MANY_MATCHED`.

- [ ] **Step 1: 핸들러 생성** (`TeamMatchEventHandler.kt`)

```kotlin
package com.org.oneulsogae.core.match.command.application

import com.org.oneulsogae.common.alarm.AlarmType
import com.org.oneulsogae.core.alarm.command.application.port.`in`.SaveAlarmUseCase
import com.org.oneulsogae.core.alarm.command.application.port.`in`.command.SaveAlarmCommand
import com.org.oneulsogae.core.match.command.domain.event.TeamMatchAccepted
import com.org.oneulsogae.core.match.command.domain.event.TeamMatchInterestSent
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 팀 매칭(관심/성사) 도메인 이벤트의 후속 알림 처리를 한곳에서 다루는 핸들러.
 * (팀 초대 관련은 [TeamEventHandler], 1:1 매칭은 [MatchEventHandler]가 담당한다)
 *
 * 알림은 부가 효과이므로 커밋 이후(AFTER_COMMIT) 별도 트랜잭션([Propagation.REQUIRES_NEW])으로 best-effort 저장한다.
 * (알림 저장이 실패해도 관심/성사/과금/채팅방은 롤백되지 않는다)
 * 팀 단위 알림이라 개인 닉네임 대신 팀 수준 문구를 쓴다. (한 팀은 2인이라 한 명의 닉네임 노출이 부적절)
 */
@Component
class TeamMatchEventHandler(
	private val saveAlarmUseCase: SaveAlarmUseCase,
) {

	/** 팀 관심 보내기 → 상대 팀 구성원들에게 "관심 받음" 알림. */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	fun onTeamMatchInterestSent(event: TeamMatchInterestSent) {
		event.recipientUserIds.forEach { recipientUserId: Long ->
			saveAlarmUseCase.save(
				SaveAlarmCommand(
					userId = recipientUserId,
					type = AlarmType.MANY_TO_MANY_INTEREST_RECEIVED,
					title = "새로운 관심",
					description = "상대 팀이 회원님 팀에 관심을 보냈어요.",
					link = "/",
					fromTeamId = event.senderTeamId,
				),
			)
		}
	}

	/** 팀 매칭 성사 → 행위자를 제외한 양 팀 구성원들에게 "매칭 성사" 알림. */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	fun onTeamMatchAccepted(event: TeamMatchAccepted) {
		event.recipientUserIds.forEach { recipientUserId: Long ->
			saveAlarmUseCase.save(
				SaveAlarmCommand(
					userId = recipientUserId,
					type = AlarmType.MANY_TO_MANY_MATCHED,
					title = "매칭 성사",
					description = "상대 팀과 매칭되었어요!",
					link = "/chat",
				),
			)
		}
	}
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :oneulsogae-core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/TeamMatchEventHandler.kt
git commit -m "feat(match): 팀 매칭 관심·성사 알림 처리 TeamMatchEventHandler 추가"
```

---

### Task 7: API — `TeamMatchController` + `TeamMatchResponse`

**Files:**
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/match/TeamMatchController.kt`
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/match/response/TeamMatchResponse.kt`

**Interfaces:**
- Consumes: `SendTeamInterestUseCase` (Task 4/5), `TeamMatch` 도메인, `AuthUser`/`LoginUser`, `ApiResponse`.
- Produces: `POST /team-matches/v1/{teamMatchId}/interest`, `TeamMatchResponse.of(teamMatch: TeamMatch)`.

- [ ] **Step 1: 응답 DTO 생성** (`TeamMatchResponse.kt`)

```kotlin
package com.org.oneulsogae.api.match.response

import com.org.oneulsogae.common.match.MatchStatus
import com.org.oneulsogae.common.match.MatchedTeamStatus
import com.org.oneulsogae.core.match.command.domain.MatchedTeam
import com.org.oneulsogae.core.match.command.domain.TeamMatch

/**
 * 팀 매칭 관심/수락 결과 응답. 매칭 상태와 참가 팀별 상태를 담는다.
 */
data class TeamMatchResponse(
	val teamMatchId: Long,
	val status: MatchStatus,
	val matchedTeams: List<MatchedTeamView>,
) {

	data class MatchedTeamView(
		val teamId: Long,
		val status: MatchedTeamStatus,
	)

	companion object {

		fun of(teamMatch: TeamMatch): TeamMatchResponse =
			TeamMatchResponse(
				teamMatchId = teamMatch.id,
				status = teamMatch.status,
				matchedTeams = teamMatch.matchedTeams.values.map { matchedTeam: MatchedTeam ->
					MatchedTeamView(teamId = matchedTeam.teamId, status = matchedTeam.status)
				},
			)
	}
}
```

- [ ] **Step 2: 컨트롤러 생성** (`TeamMatchController.kt`)

```kotlin
package com.org.oneulsogae.api.match

import com.org.oneulsogae.api.match.response.TeamMatchResponse
import com.org.oneulsogae.auth.AuthUser
import com.org.oneulsogae.auth.LoginUser
import com.org.oneulsogae.core.common.response.ApiResponse
import com.org.oneulsogae.core.match.command.application.port.`in`.SendTeamInterestUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 2:2(팀) 매칭의 팀 매칭(소개) 엔드포인트. (인증 필요)
 * - POST /{teamMatchId}/interest: 참가 팀의 ACTIVE 구성원이 팀을 대표해 관심을 보낸다.
 *   상대 팀이 아직 신청 안 했으면 신청(PARTIALLY_ACCEPTED), 이미 신청했으면 수락이 되어 성사(MATCHED)된다.
 */
@Tag(name = "팀 매칭(소개)", description = "결성된 두 팀의 매칭에 관심을 보내고 성사시키는 엔드포인트.")
@RestController
@RequestMapping("/team-matches/v1")
class TeamMatchController(
	private val sendTeamInterestUseCase: SendTeamInterestUseCase,
) {

	@Operation(
		summary = "팀 관심 보내기",
		description = "참가 팀의 ACTIVE 구성원이 팀을 대표해 관심을 보낸다. 상대 팀이 이미 신청했으면 성사(MATCHED)되어 4인 채팅방이 생성된다. 신청/수락 비용은 행위한 구성원이 부담한다.",
	)
	@PostMapping("/{teamMatchId}/interest")
	fun sendInterest(
		@LoginUser user: AuthUser,
		@PathVariable teamMatchId: Long,
	): ApiResponse<TeamMatchResponse> =
		ApiResponse.success(TeamMatchResponse.of(sendTeamInterestUseCase.sendInterest(user.id, teamMatchId)))
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :oneulsogae-api:compileKotlin`
Expected: BUILD SUCCESSFUL. (`ApiResponse.success`/`@LoginUser` 사용법은 `TeamController.kt`와 동일)

- [ ] **Step 4: 커밋**

```bash
git add oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/match/TeamMatchController.kt \
        oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/match/response/TeamMatchResponse.kt
git commit -m "feat(match): 팀 매칭 관심 보내기 TeamMatchController·TeamMatchResponse 추가"
```

---

### Task 8: E2E 테스트 — `SendTeamInterestE2ETest`

실서버 + Testcontainers로 신청/성사/코인부족/비참가/미인증을 검증한다. `DisbandTeamMatchTeardownE2ETest`(팀 결성·팀 매칭 픽스처)와 `SendInterestE2ETest`(코인·알림·채팅 단언)를 참고한다.

**Files:**
- Create: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/SendTeamInterestE2ETest.kt`

**Interfaces:**
- Consumes: `POST /team-matches/v1/{teamMatchId}/interest` (Task 7), 픽스처 `MatchUserEntityFixture`/`CoinBalanceEntityFixture`, `IntegrationUtil`, RestAssured DSL(`post`/`expect`).

- [ ] **Step 1: E2E 테스트 작성** (`SendTeamInterestE2ETest.kt`)

```kotlin
package com.org.oneulsogae.api.match

import com.org.oneulsogae.common.alarm.AlarmType
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.common.match.MatchStatus
import com.org.oneulsogae.common.match.MatchedTeamStatus
import com.org.oneulsogae.common.match.TeamMatchType
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.alarm.command.entity.AlarmEntity
import com.org.oneulsogae.infra.alarm.command.entity.QAlarmEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomMemberEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinBalanceEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinHistoryEntity
import com.org.oneulsogae.infra.fixture.CoinBalanceEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.MatchUserEntityFixture
import com.org.oneulsogae.infra.match.command.entity.MatchedTeamEntity
import com.org.oneulsogae.infra.match.command.entity.QMatchUserEntity
import com.org.oneulsogae.infra.match.command.entity.QMatchedTeamEntity
import com.org.oneulsogae.infra.match.command.entity.QTeamEntity
import com.org.oneulsogae.infra.match.command.entity.QTeamMatchEntity
import com.org.oneulsogae.infra.match.command.entity.QTeamMemberEntity
import com.org.oneulsogae.infra.match.command.entity.TeamMatchEntity
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * `POST /team-matches/v1/{teamMatchId}/interest` E2E. (팀 매칭 신청/수락 통합 엔드포인트)
 * 결성(ACTIVE)된 두 팀을 초대→수락으로 만들고, 두 팀을 묶은 팀 매칭을 준비한 뒤 관심을 보낸다.
 * 실서버(RANDOM_PORT) + Testcontainers(MySQL/Redis, 분산 락 포함)를 기동하고 HTTP를 호출한다.
 */
class SendTeamInterestE2ETest : AbstractIntegrationSupport({

	fun persistMatchUser(userId: Long, gender: Gender) {
		IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = gender))
	}

	// 결성(ACTIVE)까지 진행한 팀의 teamId를 돌려준다. (초대 → 수락)
	fun formedTeam(ownerId: Long, invitedUserId: Long): Long {
		persistMatchUser(ownerId, Gender.MALE)
		persistMatchUser(invitedUserId, Gender.MALE)
		val teamId: Long = post("/teams/v1/invitation") {
			bearer(accessTokenFor(ownerId))
			jsonBody("""{"invitedUserId": $invitedUserId, "regionId": 1, "name": "우리팀", "introduction": "함께 즐겁게 활동할 팀이에요"}""")
		}.extract().path<Int>("data.teamId").toLong()
		post("/teams/v1/$teamId/acceptance") { bearer(accessTokenFor(invitedUserId)) }
		return teamId
	}

	// 두 팀을 참가 팀으로 하는 팀 매칭을 만들고 teamMatchId를 돌려준다. (각 팀 상태·헤더 상태 지정 가능)
	fun persistTeamMatch(
		myTeamId: Long,
		opponentTeamId: Long,
		headerStatus: MatchStatus = MatchStatus.PROPOSED,
		myStatus: MatchedTeamStatus = MatchedTeamStatus.WAITING,
		opponentStatus: MatchedTeamStatus = MatchedTeamStatus.WAITING,
	): Long {
		val header: TeamMatchEntity = IntegrationUtil.persist(
			TeamMatchEntity(
				memberKey = listOf(myTeamId, opponentTeamId).sorted().joinToString("-"),
				introducedDate = LocalDate.of(2026, 6, 24),
				expiresAt = LocalDateTime.of(2026, 6, 25, 12, 0),
				status = headerStatus,
				matchType = TeamMatchType.RECOMMENDED,
				dateInitAmount = 40,
				dateAcceptAmount = 40,
			),
		)
		val teamMatchId: Long = header.id!!
		IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = teamMatchId, teamId = myTeamId, status = myStatus))
		IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = teamMatchId, teamId = opponentTeamId, status = opponentStatus))
		return teamMatchId
	}

	describe("POST /team-matches/v1/{teamMatchId}/interest") {

		context("상대 팀이 아직 신청 안 한 팀 매칭에 관심을 보내면") {
			it("신청 비용(40)이 차감되고 PARTIALLY_ACCEPTED가 된다 + 상대 팀 2인에게 관심 알림, 채팅방 없음") {
				val myOwner = 6001L
				val myInvited = 6002L
				val oppOwner = 6003L
				val oppInvited = 6004L
				val myTeamId: Long = formedTeam(myOwner, myInvited)
				val opponentTeamId: Long = formedTeam(oppOwner, oppInvited)
				val teamMatchId: Long = persistTeamMatch(myTeamId, opponentTeamId)
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = myOwner, balance = 100))

				post("/team-matches/v1/$teamMatchId/interest") {
					bearer(accessTokenFor(myOwner))
				} expect {
					status(200)
					body("success", true)
					body("data.status", MatchStatus.PARTIALLY_ACCEPTED.name)
				}

				// 신청 비용(MEETING_INIT=40) 차감 → 잔액 60
				coinBalanceOf(myOwner) shouldBe 60
				// 미성사라 채팅방 없음 (matchId == teamMatchId 전제)
				chatRoomMemberCount(teamMatchId) shouldBe 0
				// 상대 팀 2인에게만 관심 알림, 내 팀은 받지 않음
				interestAlarms(oppOwner).size shouldBe 1
				interestAlarms(oppInvited).size shouldBe 1
				interestAlarms(myOwner).size shouldBe 0
				interestAlarms(myInvited).size shouldBe 0
				interestAlarms(oppOwner).first().fromTeamId shouldBe myTeamId
			}
		}

		context("상대 팀이 이미 신청한 팀 매칭에 관심을 보내면") {
			it("수락 비용(40)이 차감되고 MATCHED가 된다 + 4인 채팅방 생성 + 행위자 제외 3인에게 성사 알림") {
				val myOwner = 6101L
				val myInvited = 6102L
				val oppOwner = 6103L
				val oppInvited = 6104L
				val myTeamId: Long = formedTeam(myOwner, myInvited)
				val opponentTeamId: Long = formedTeam(oppOwner, oppInvited)
				// 상대 팀이 이미 APPLY한 PARTIALLY_ACCEPTED 매칭
				val teamMatchId: Long = persistTeamMatch(
					myTeamId = myTeamId,
					opponentTeamId = opponentTeamId,
					headerStatus = MatchStatus.PARTIALLY_ACCEPTED,
					myStatus = MatchedTeamStatus.WAITING,
					opponentStatus = MatchedTeamStatus.APPLY,
				)
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = myOwner, balance = 100))

				post("/team-matches/v1/$teamMatchId/interest") {
					bearer(accessTokenFor(myOwner))
				} expect {
					status(200)
					body("data.status", MatchStatus.MATCHED.name)
				}

				// 수락 비용(MEETING_ACCEPT=40) 차감 → 잔액 60
				coinBalanceOf(myOwner) shouldBe 60
				// 성사 → 4인 채팅방 생성 (matchId == teamMatchId)
				chatRoomMemberCount(teamMatchId) shouldBe 4
				// 행위자(myOwner) 제외한 3인에게 성사 알림
				matchedAlarms(myInvited).size shouldBe 1
				matchedAlarms(oppOwner).size shouldBe 1
				matchedAlarms(oppInvited).size shouldBe 1
				matchedAlarms(myOwner).size shouldBe 0
			}
		}

		context("코인 잔액이 부족하면") {
			it("400(COIN-001)을 반환하고 잔액·매칭 상태가 그대로다") {
				val myOwner = 6201L
				val myInvited = 6202L
				val oppOwner = 6203L
				val oppInvited = 6204L
				val myTeamId: Long = formedTeam(myOwner, myInvited)
				val opponentTeamId: Long = formedTeam(oppOwner, oppInvited)
				val teamMatchId: Long = persistTeamMatch(myTeamId, opponentTeamId)
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = myOwner, balance = 10)) // 40보다 적음

				post("/team-matches/v1/$teamMatchId/interest") {
					bearer(accessTokenFor(myOwner))
				} expect {
					status(400)
					body("success", false)
					body("error.code", "COIN-001")
				}

				// 차감 실패로 롤백 → 잔액 유지 + 매칭 PROPOSED 그대로
				coinBalanceOf(myOwner) shouldBe 10
				teamMatchStatus(teamMatchId) shouldBe MatchStatus.PROPOSED
			}
		}

		context("참가 팀 구성원이 아닌 사용자가 관심을 보내면") {
			it("403(TEAM-MATCH-002)을 반환한다") {
				val myOwner = 6301L
				val myInvited = 6302L
				val oppOwner = 6303L
				val oppInvited = 6304L
				val outsider = 6399L
				val myTeamId: Long = formedTeam(myOwner, myInvited)
				val opponentTeamId: Long = formedTeam(oppOwner, oppInvited)
				val teamMatchId: Long = persistTeamMatch(myTeamId, opponentTeamId)
				persistMatchUser(outsider, Gender.MALE)
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = outsider, balance = 100))

				post("/team-matches/v1/$teamMatchId/interest") {
					bearer(accessTokenFor(outsider))
				} expect {
					status(403)
					body("success", false)
					body("error.code", "TEAM-MATCH-002")
				}
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				post("/team-matches/v1/1/interest") {} expect {
					status(401)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QAlarmEntity.alarmEntity)
		IntegrationUtil.deleteAll(QChatRoomMemberEntity.chatRoomMemberEntity)
		IntegrationUtil.deleteAll(QChatRoomEntity.chatRoomEntity)
		IntegrationUtil.deleteAll(QCoinBalanceEntity.coinBalanceEntity)
		IntegrationUtil.deleteAll(QCoinHistoryEntity.coinHistoryEntity)
		IntegrationUtil.deleteAll(QMatchedTeamEntity.matchedTeamEntity)
		IntegrationUtil.deleteAll(QTeamMatchEntity.teamMatchEntity)
		IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
		IntegrationUtil.deleteAll(QTeamEntity.teamEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
	}
})

private fun coinBalanceOf(userId: Long): Int {
	val q = QCoinBalanceEntity.coinBalanceEntity
	return IntegrationUtil.getQuery().select(q.balance).from(q).where(q.userId.eq(userId)).fetchOne()!!
}

private fun teamMatchStatus(teamMatchId: Long): MatchStatus {
	val q = QTeamMatchEntity.teamMatchEntity
	return IntegrationUtil.getQuery().select(q.status).from(q).where(q.id.eq(teamMatchId)).fetchOne()!!
}

// matchId == teamMatchId인 채팅방의 참가자 수. (성사 시 4인 채팅방 생성 확인)
private fun chatRoomMemberCount(teamMatchId: Long): Long {
	val room = QChatRoomEntity.chatRoomEntity
	val member = QChatRoomMemberEntity.chatRoomMemberEntity
	return IntegrationUtil.getQuery()
		.select(member.count())
		.from(room)
		.join(member).on(member.chatRoomId.eq(room.id))
		.where(room.matchId.eq(teamMatchId))
		.fetchOne() ?: 0L
}

private fun interestAlarms(userId: Long): List<AlarmEntity> {
	val q = QAlarmEntity.alarmEntity
	return IntegrationUtil.getQuery().selectFrom(q)
		.where(q.userId.eq(userId).and(q.type.eq(AlarmType.MANY_TO_MANY_INTEREST_RECEIVED))).fetch()
}

private fun matchedAlarms(userId: Long): List<AlarmEntity> {
	val q = QAlarmEntity.alarmEntity
	return IntegrationUtil.getQuery().selectFrom(q)
		.where(q.userId.eq(userId).and(q.type.eq(AlarmType.MANY_TO_MANY_MATCHED))).fetch()
}
```

> 참고: `chatRoomMemberCount`의 `QChatRoomEntity`에 `matchId` 경로가 있는지 `QChatRoomEntity`에서 확인한다. (도메인 `ChatRoom.matchId`가 엔티티에 매핑돼 있으므로 존재) 없으면 채팅방 1개 존재 + `QChatRoomMemberEntity`로 4행을 각각 센다.

- [ ] **Step 2: E2E 실행 (전체 통과 확인)**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.match.SendTeamInterestE2ETest"`
Expected: 5개 컨텍스트 모두 PASS. (Testcontainers Docker 필요)

- [ ] **Step 3: 전체 빌드 확인**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋**

```bash
git add oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/SendTeamInterestE2ETest.kt
git commit -m "test(match): 팀 매칭 관심 신청·수락 E2E 추가"
```

---

## Self-Review

**1. Spec coverage:**
- API 경계 `POST /team-matches/v1/{teamMatchId}/interest` + `TeamMatchController`/`TeamMatchResponse` → Task 7. ✅
- `SendTeamInterestService`(행위자 식별·검증·코인·채팅·이벤트 분기) → Task 5. ✅
- 도메인 `TeamMatch.respond`/`validateRespondable`/`MatchedTeams` 메서드 → Task 1, 2. ✅
- 이벤트 `TeamMatchInterestSent`/`TeamMatchAccepted` + `TeamMatchEventHandler` + `MANY_TO_MANY_*` 재사용 → Task 4, 6. ✅
- `GetTeamMatchPort.findById` + 어댑터 → Task 3. ✅
- `TeamMatchErrorCode` + `TEAM_MATCH_INTEREST` 락 → Task 2, 4. ✅
- 테스트(도메인 유닛 + api E2E) → Task 1, 2, 8. ✅
- 미포함(조회 API, 채팅 키 재설계, 재신청 가드)은 스펙대로 범위 밖. ✅

**2. Placeholder scan:** 모든 스텝에 실제 코드/명령/기대출력 포함. "참고" 주석은 구현자가 확인할 검증 지점(시그니처 일치 확인)이며 플레이스홀더가 아님.

**3. Type consistency:**
- `MatchedTeams.apply(teamId)`/`allApplied`/`anyApplied`/`activateAll`/`isParticipant` — Task 1 정의 ↔ Task 2 `TeamMatch.respond`/`validateRespondable`에서 사용. 일치. ✅
- `TeamMatch.respond(teamId: Long): TeamMatch`, `validateRespondable(teamId: Long)` — Task 2 정의 ↔ Task 5 서비스 사용. 일치. ✅
- `GetTeamMatchPort.findById(teamMatchId): TeamMatch?` — Task 3 정의 ↔ Task 5 사용. 일치. ✅
- 이벤트 필드 `TeamMatchInterestSent(teamMatchId, senderTeamId, recipientUserIds)`, `TeamMatchAccepted(teamMatchId, recipientUserIds)` — Task 4 정의 ↔ Task 5 발행 ↔ Task 6 소비. 일치. ✅
- `SendTeamInterestUseCase.sendInterest(userId, teamMatchId): TeamMatch` — Task 4 정의 ↔ Task 5 구현 ↔ Task 7 컨트롤러 호출. 일치. ✅
- 코인 금액: `MEETING_INIT/ACCEPT = 40` → E2E 잔액 100−40=60 단언과 일치. ✅
