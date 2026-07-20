# 팀 해체 시 매칭 종료·채팅 차단·상대 알림 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 팀 해체 시 미성사 팀 매칭은 종료(CLOSED)하고, 성사(MATCHED) 매칭은 유지하되 나가는 팀원의 채팅 참가를 비활성화하며, 상대 팀 활성 구성원에게 알림을 보낸다.

**Architecture:** `DisbandTeamService`(match 도메인) 단일 트랜잭션에서 팀 해체 후 진행 중 매칭을 상태별로 정리하고(미성사 → `TeamMatch.close()`, 성사 → chat in-port로 멤버 비활성화), 알림 수신자 userId를 모아 도메인 이벤트를 발행한다. 알림은 기존 패턴대로 `TeamEventHandler`가 커밋 이후(AFTER_COMMIT) best-effort로 저장한다.

**Tech Stack:** Kotlin 2.2 / Spring Boot 4 / Spring Data JPA / QueryDSL, Kotest(도메인 유닛), Testcontainers + RestAssured(E2E).

## Global Constraints

- 헥사고날: Controller→in-port, Service→out-port, infra Adapter가 out-port 구현. 다른 도메인은 in-port UseCase만 주입.
- CQRS: command 경로는 query dao(`GetMatchedTeamDao`)를 재사용하지 않고 자기 command out-port를 쓴다.
- 타입 명시: 변수·반환·람다 파라미터 타입을 생략하지 않는다(표현식 본문 포함).
- 현재 시각은 `TimeGenerator.now()`로 얻어 도메인에 파라미터로 주입한다(`LocalDateTime.now()` 직접 호출 금지).
- 일급 컬렉션은 원시 List 대신 `Xs` 래퍼로 동작을 응집한다.
- 도메인 모델은 Kotest 유닛, `oneulsogae-api` 경계는 E2E(`AbstractIntegrationSupport` + `IntegrationUtil` + 엔티티 픽스처).
- 새 알림 문구(확정): title `"상대 팀 해체"`, description `"상대 팀이 해체되었어요."`, `link = ""`(없음), `fromTeamId = 해체된 팀 id`.
- 도메인 유닛 테스트는 `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/...` 아래에 둔다(기존 컨벤션).
- 테스트 실행 모듈은 `:oneulsogae-api`.

---

### Task 1: MatchedTeams 도메인 메서드 (deactivateAll, opponentTeamIdOf)

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/MatchedTeams.kt`
- Test(Create): `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/match/MatchedTeamsTest.kt`

**Interfaces:**
- Consumes: 기존 `MatchedTeam.deactivate(): MatchedTeam`, `MatchedTeam.teamId: Long`, `MatchedTeam.status: MatchedTeamStatus`
- Produces:
  - `MatchedTeams.deactivateAll(): MatchedTeams` — 모든 참가 팀을 DEACTIVE로 전이한 새 컬렉션
  - `MatchedTeams.opponentTeamIdOf(teamId: Long): Long` — `teamId`가 아닌 참가 팀의 teamId

- [ ] **Step 1: Write the failing test**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/match/MatchedTeamsTest.kt`
```kotlin
package com.org.oneulsogae.domain.match

import com.org.oneulsogae.common.match.MatchedTeamStatus
import com.org.oneulsogae.core.match.command.domain.MatchedTeams
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [MatchedTeams] 일급 컬렉션 유닛 테스트.
 * 참가 팀 일괄 비활성화와 상대 팀 식별을 검증한다.
 */
class MatchedTeamsTest : DescribeSpec({

    describe("deactivateAll") {
        it("모든 참가 팀을 DEACTIVE로 전이한 새 컬렉션을 돌려준다") {
            val matchedTeams: MatchedTeams = MatchedTeams.of(listOf(10L, 20L))

            val deactivated: MatchedTeams = matchedTeams.deactivateAll()

            deactivated.values.all { it.status == MatchedTeamStatus.DEACTIVE } shouldBe true
            // 원본 불변
            matchedTeams.values.all { it.status == MatchedTeamStatus.WAITING } shouldBe true
        }
    }

    describe("opponentTeamIdOf") {
        it("주어진 teamId가 아닌 상대 팀의 teamId를 돌려준다") {
            val matchedTeams: MatchedTeams = MatchedTeams.of(listOf(10L, 20L))

            matchedTeams.opponentTeamIdOf(10L) shouldBe 20L
            matchedTeams.opponentTeamIdOf(20L) shouldBe 10L
        }
    }
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.match.MatchedTeamsTest"`
Expected: 컴파일 실패 — `deactivateAll`/`opponentTeamIdOf` 미정의

- [ ] **Step 3: Write minimal implementation**

`MatchedTeams.kt`의 `withTeamMatchId(...)` 메서드 아래(`companion object` 위)에 추가:
```kotlin
	/** 모든 참가 팀을 비활성(DEACTIVE)으로 전이한 새 컬렉션. (팀 해체로 미성사 매칭을 종료할 때) */
	fun deactivateAll(): MatchedTeams =
		MatchedTeams(values.map { matchedTeam: MatchedTeam -> matchedTeam.deactivate() })

	/** [teamId]가 아닌 상대 팀의 teamId. (2:2 매칭이므로 한 팀) */
	fun opponentTeamIdOf(teamId: Long): Long =
		values.first { matchedTeam: MatchedTeam -> matchedTeam.teamId != teamId }.teamId
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.match.MatchedTeamsTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/MatchedTeams.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/match/MatchedTeamsTest.kt
git commit -m "feat(match): MatchedTeams 일괄 비활성화·상대 팀 식별 메서드 추가"
```

---

### Task 2: TeamMatch 도메인 메서드 (close, isMatched, opponentTeamIdOf)

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/TeamMatch.kt`
- Test(Modify): `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/match/TeamMatchTest.kt`

**Interfaces:**
- Consumes: `MatchedTeams.deactivateAll()`, `MatchedTeams.opponentTeamIdOf(teamId)` (Task 1), `MatchStatus.{CLOSED,MATCHED}`
- Produces:
  - `TeamMatch.close(): TeamMatch` — status=CLOSED + 참가 팀 전원 DEACTIVE
  - `TeamMatch.isMatched(): Boolean` — status==MATCHED
  - `TeamMatch.opponentTeamIdOf(teamId: Long): Long`

- [ ] **Step 1: Write the failing test**

`TeamMatchTest.kt`의 마지막 `describe(...)` 블록 뒤(닫는 `})` 직전)에 추가:
```kotlin
	describe("close - 미성사 매칭 종료") {
		it("status를 CLOSED로 바꾸고 참가 팀 전원을 DEACTIVE로 전이한다") {
			val teamMatch: TeamMatch = TeamMatch.propose(
				teamAId = 10L,
				teamBId = 20L,
				matchType = TeamMatchType.RECOMMENDED,
				now = now,
			)

			val closed: TeamMatch = teamMatch.close()

			closed.status shouldBe MatchStatus.CLOSED
			closed.matchedTeams.values.all { it.status == MatchedTeamStatus.DEACTIVE } shouldBe true
		}
	}

	describe("isMatched / opponentTeamIdOf") {
		it("isMatched는 status가 MATCHED일 때만 true다") {
			val proposed: TeamMatch = TeamMatch.propose(10L, 20L, TeamMatchType.RECOMMENDED, now)

			proposed.isMatched() shouldBe false
			proposed.copy(status = MatchStatus.MATCHED).isMatched() shouldBe true
		}

		it("opponentTeamIdOf는 내 팀이 아닌 상대 팀 id를 돌려준다") {
			val teamMatch: TeamMatch = TeamMatch.propose(10L, 20L, TeamMatchType.RECOMMENDED, now)

			teamMatch.opponentTeamIdOf(10L) shouldBe 20L
		}
	}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.match.TeamMatchTest"`
Expected: 컴파일 실패 — `close`/`isMatched`/`opponentTeamIdOf` 미정의

- [ ] **Step 3: Write minimal implementation**

`TeamMatch.kt`의 `matchedTeamsWith(...)` 메서드 아래(`companion object` 위)에 추가:
```kotlin
	/** 미성사 매칭을 종료한 새 모델. status를 CLOSED로 바꾸고 참가 팀 전원을 DEACTIVE로 전이한다. (기록은 보존, 소프트 삭제 안 함) */
	fun close(): TeamMatch =
		copy(status = MatchStatus.CLOSED, matchedTeams = matchedTeams.deactivateAll())

	/** 성사(MATCHED)된 매칭인지 여부. */
	fun isMatched(): Boolean =
		status == MatchStatus.MATCHED

	/** [teamId]가 아닌 상대 팀의 teamId. */
	fun opponentTeamIdOf(teamId: Long): Long =
		matchedTeams.opponentTeamIdOf(teamId)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.match.TeamMatchTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/TeamMatch.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/match/TeamMatchTest.kt
git commit -m "feat(match): TeamMatch 종료·성사판정·상대팀 식별 메서드 추가"
```

---

### Task 3: ChatRoomMembers.deactivate(userIds) 일급 컬렉션 메서드

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/chat/command/domain/ChatRoomMembers.kt`
- Test(Create): `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/chat/ChatRoomMembersTest.kt`

**Interfaces:**
- Consumes: 기존 `ChatRoomMember.deactivate(): ChatRoomMember`, `ChatRoomMember.userId`, `ChatRoomMember.status`
- Produces: `ChatRoomMembers.deactivate(userIds: Set<Long>): ChatRoomMembers` — `userIds`에 포함된 참가자만 DEACTIVE로 전이한 (대상만 담은) 컬렉션

- [ ] **Step 1: Write the failing test**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/chat/ChatRoomMembersTest.kt`
```kotlin
package com.org.oneulsogae.domain.chat

import com.org.oneulsogae.common.chat.ChatRoomMemberStatus
import com.org.oneulsogae.core.chat.command.domain.ChatRoomMember
import com.org.oneulsogae.core.chat.command.domain.ChatRoomMembers
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

/**
 * [ChatRoomMembers] 일급 컬렉션 유닛 테스트.
 * 지정한 userId 참가자만 비활성화하는 동작을 검증한다.
 */
class ChatRoomMembersTest : DescribeSpec({

    val now: LocalDateTime = LocalDateTime.of(2026, 6, 24, 12, 0)

    fun member(userId: Long): ChatRoomMember =
        ChatRoomMember(chatRoomId = 1L, userId = userId, joinedAt = now)

    describe("deactivate(userIds)") {
        it("지정한 userId 참가자만 DEACTIVE로 전이해 그 대상만 담아 돌려준다") {
            val members: ChatRoomMembers = ChatRoomMembers(listOf(member(1L), member(2L), member(3L)))

            val result: ChatRoomMembers = members.deactivate(setOf(1L, 3L))

            result.values.map { it.userId } shouldBe listOf(1L, 3L)
            result.values.all { it.status == ChatRoomMemberStatus.DEACTIVE } shouldBe true
        }

        it("대상이 없으면 빈 컬렉션을 돌려준다") {
            val members: ChatRoomMembers = ChatRoomMembers(listOf(member(1L)))

            members.deactivate(setOf(99L)).values shouldBe emptyList()
        }
    }
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.chat.ChatRoomMembersTest"`
Expected: 컴파일 실패 — `deactivate(Set<Long>)` 미정의

- [ ] **Step 3: Write minimal implementation**

`ChatRoomMembers.kt`의 `delete(now)` 메서드 아래(`companion object` 위)에 추가:
```kotlin
	/** [userIds]에 해당하는 참가자만 비활성(DEACTIVE)으로 전이한 (대상만 담은) 컬렉션을 반환한다. (팀 해체로 그 팀원의 채팅 입장을 막을 때) */
	fun deactivate(userIds: Set<Long>): ChatRoomMembers =
		ChatRoomMembers(values.filter { it.userId in userIds }.map { it.deactivate() })
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.chat.ChatRoomMembersTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/chat/command/domain/ChatRoomMembers.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/chat/ChatRoomMembersTest.kt
git commit -m "feat(chat): ChatRoomMembers 지정 userId 일괄 비활성화 메서드 추가"
```

---

### Task 4: GetTeamMatchPort out-port + TeamMatchAdapter 구현 + repo 파생 쿼리

**Files:**
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/port/out/GetTeamMatchPort.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/adapter/TeamMatchAdapter.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/repository/TeamMatchJpaRepository.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/repository/MatchedTeamJpaRepository.kt`

**Interfaces:**
- Consumes: `TeamMatchEntity.toDomain(matchedTeams)`, `MatchedTeamEntity.toDomain()`, `MatchedTeams`, `MatchStatus.CLOSED`
- Produces: `GetTeamMatchPort.findActiveByTeamId(teamId: Long): List<TeamMatch>` — 해당 팀이 참가했고 status가 CLOSED가 아닌 팀 매칭들(참가 팀 전원 포함)

이 태스크는 내부 포트라 HTTP 경계가 없어 동작 검증은 Task 7 E2E가 담당한다. 본 태스크의 게이트는 **빌드 통과**다.

- [ ] **Step 1: Create the out-port**

`GetTeamMatchPort.kt`
```kotlin
package com.org.oneulsogae.core.match.command.application.port.out

import com.org.oneulsogae.core.match.command.domain.TeamMatch

/**
 * 팀 매칭 애그리거트(헤더 + 참가 팀) 조회 포트. (command 흐름에서 변경/정리 대상 로드)
 * 구현은 infra의 [com.org.oneulsogae.infra.match.command.adapter.TeamMatchAdapter]가 담당한다.
 */
interface GetTeamMatchPort {

	/** [teamId]가 참가했고 아직 종료(CLOSED)되지 않은 팀 매칭들을 (참가 팀 전원과 함께) 조회한다. 없으면 빈 목록. */
	fun findActiveByTeamId(teamId: Long): List<TeamMatch>
}
```

- [ ] **Step 2: Add repository derived queries**

`TeamMatchJpaRepository.kt` 전체를 교체:
```kotlin
package com.org.oneulsogae.infra.match.command.repository

import com.org.oneulsogae.common.match.MatchStatus
import com.org.oneulsogae.infra.match.command.entity.TeamMatchEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 팀 매칭 헤더(team_matches) 리포지토리.
 * [com.org.oneulsogae.infra.match.command.adapter.TeamMatchAdapter]가 헤더 저장·조회에 사용한다.
 */
interface TeamMatchJpaRepository : JpaRepository<TeamMatchEntity, Long> {

	/** 주어진 id들 중 status가 [status]가 아닌 헤더들. (종료되지 않은 진행 중 매칭 선별, PK IN seek) */
	fun findByIdInAndStatusNot(ids: List<Long>, status: MatchStatus): List<TeamMatchEntity>
}
```

`MatchedTeamJpaRepository.kt` 전체를 교체:
```kotlin
package com.org.oneulsogae.infra.match.command.repository

import com.org.oneulsogae.infra.match.command.entity.MatchedTeamEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 팀 매칭 참가 팀(matched_teams) 리포지토리.
 * [com.org.oneulsogae.infra.match.command.adapter.TeamMatchAdapter]가 참가 팀 행 저장·조회에 사용한다.
 */
interface MatchedTeamJpaRepository : JpaRepository<MatchedTeamEntity, Long> {

	/** 팀별 참가 행. (idx_team_id seek) */
	fun findByTeamId(teamId: Long): List<MatchedTeamEntity>

	/** 매칭들의 참가 팀 행 전부. (ux_team_match_id_team_id 선두 team_match_id seek) */
	fun findByTeamMatchIdIn(teamMatchIds: List<Long>): List<MatchedTeamEntity>
}
```

- [ ] **Step 3: Implement the port in TeamMatchAdapter**

`TeamMatchAdapter.kt` 전체를 교체:
```kotlin
package com.org.oneulsogae.infra.match.command.adapter

import com.org.oneulsogae.common.match.MatchStatus
import com.org.oneulsogae.core.match.command.application.port.out.GetTeamMatchPort
import com.org.oneulsogae.core.match.command.application.port.out.SaveTeamMatchPort
import com.org.oneulsogae.core.match.command.domain.MatchedTeams
import com.org.oneulsogae.core.match.command.domain.TeamMatch
import com.org.oneulsogae.infra.match.command.entity.MatchedTeamEntity
import com.org.oneulsogae.infra.match.command.entity.TeamMatchEntity
import com.org.oneulsogae.infra.match.command.mapper.toDomain
import com.org.oneulsogae.infra.match.command.mapper.toEntities
import com.org.oneulsogae.infra.match.command.mapper.toEntity
import com.org.oneulsogae.infra.match.command.repository.MatchedTeamJpaRepository
import com.org.oneulsogae.infra.match.command.repository.TeamMatchJpaRepository
import org.springframework.stereotype.Component

/**
 * [TeamMatchEntity]의 command 영속성 어댑터. ([SaveTeamMatchPort], [GetTeamMatchPort] 구현)
 * 팀 매칭은 헤더(team_matches) + 참가 팀(matched_teams)으로 이뤄진 하나의 애그리거트이므로,
 * 이 어댑터가 두 테이블의 영속화·조회를 함께 책임진다. (헤더 저장 → id 획득 → 그 id로 참가 팀 행 저장)
 */
@Component
class TeamMatchAdapter(
	private val teamMatchJpaRepository: TeamMatchJpaRepository,
	private val matchedTeamJpaRepository: MatchedTeamJpaRepository,
) : SaveTeamMatchPort, GetTeamMatchPort {

	override fun save(teamMatch: TeamMatch): TeamMatch {
		val savedEntity: TeamMatchEntity = teamMatchJpaRepository.save(teamMatch.toEntity())
		val teamMatchId: Long = savedEntity.id!!
		val savedMatchedTeams: MatchedTeams = MatchedTeams(
			matchedTeamJpaRepository
				.saveAll(teamMatch.matchedTeamsWith(teamMatchId).toEntities())
				.map { it.toDomain() },
		)
		return savedEntity.toDomain(savedMatchedTeams)
	}

	override fun findActiveByTeamId(teamId: Long): List<TeamMatch> {
		// ① 이 팀의 참가 행으로 소속 팀 매칭 id 수집 (idx_team_id seek)
		val teamMatchIds: List<Long> = matchedTeamJpaRepository.findByTeamId(teamId)
			.map { it.teamMatchId }
			.distinct()
		if (teamMatchIds.isEmpty()) return emptyList()

		// ② 종료(CLOSED)되지 않은 헤더만 (PK IN seek)
		val headers: List<TeamMatchEntity> = teamMatchJpaRepository.findByIdInAndStatusNot(teamMatchIds, MatchStatus.CLOSED)
		if (headers.isEmpty()) return emptyList()

		// ③ 그 헤더들의 참가 팀 전원을 한 번에 로드해 헤더별로 묶는다 (ux_team_match_id_team_id 선두 seek)
		val headerIds: List<Long> = headers.map { it.id!! }
		val membersByMatchId: Map<Long, List<MatchedTeamEntity>> = matchedTeamJpaRepository.findByTeamMatchIdIn(headerIds)
			.groupBy { it.teamMatchId }

		return headers.map { header: TeamMatchEntity ->
			val matchedTeams: MatchedTeams = MatchedTeams(
				membersByMatchId[header.id].orEmpty().map { it.toDomain() },
			)
			header.toDomain(matchedTeams)
		}
	}
}
```

- [ ] **Step 4: Verify the build**

Run: `./gradlew :oneulsogae-infra:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/port/out/GetTeamMatchPort.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/adapter/TeamMatchAdapter.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/repository/TeamMatchJpaRepository.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/match/command/repository/MatchedTeamJpaRepository.kt
git commit -m "feat(match): 팀 매칭 진행 중 조회 포트(GetTeamMatchPort)와 어댑터 추가"
```

---

### Task 5: DeactivateChatRoomMemberUseCase in-port + 서비스

**Files:**
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/chat/command/application/port/in/DeactivateChatRoomMemberUseCase.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/chat/command/application/DeactivateChatRoomMemberService.kt`

**Interfaces:**
- Consumes: `GetChatRoomPort.findByMatchId(matchId): ChatRoom?`, `GetChatRoomMemberPort.findAllByChatRoomId(chatRoomId): ChatRoomMembers`, `SaveChatRoomMemberPort.saveAll(members): ChatRoomMembers`, `ChatRoomMembers.deactivate(userIds: Set<Long>)` (Task 3)
- Produces: `DeactivateChatRoomMemberUseCase.deactivate(matchId: Long, userIds: List<Long>)` — 매칭의 채팅방에서 `userIds` 참가자를 비활성화(방 없으면 no-op)

이 태스크도 내부 in-port라 동작 검증은 Task 7 E2E가 담당한다. 게이트는 **빌드 통과**.

- [ ] **Step 1: Create the in-port**

`DeactivateChatRoomMemberUseCase.kt`
```kotlin
package com.org.oneulsogae.core.chat.command.application.port.`in`

/**
 * 매칭의 채팅방에서 특정 사용자들의 참가를 비활성화하는 인포트.
 * 팀 해체 시 나가는 팀원이 (성사된 매칭의) 채팅방에 더는 들어가지 못하도록, 그 팀원들의 참가자 행을 DEACTIVE로 전이한다.
 * 방을 닫지는 않는다. (상대 팀의 참가는 유지)
 */
interface DeactivateChatRoomMemberUseCase {

	/** [matchId]의 채팅방에서 [userIds] 참가자를 비활성화한다. 채팅방이 없으면 아무것도 하지 않는다. */
	fun deactivate(matchId: Long, userIds: List<Long>)
}
```

- [ ] **Step 2: Create the service**

`DeactivateChatRoomMemberService.kt`
```kotlin
package com.org.oneulsogae.core.chat.command.application

import com.org.oneulsogae.core.chat.command.application.port.`in`.DeactivateChatRoomMemberUseCase
import com.org.oneulsogae.core.chat.command.application.port.out.GetChatRoomMemberPort
import com.org.oneulsogae.core.chat.command.application.port.out.GetChatRoomPort
import com.org.oneulsogae.core.chat.command.application.port.out.SaveChatRoomMemberPort
import com.org.oneulsogae.core.chat.command.domain.ChatRoom
import com.org.oneulsogae.core.chat.command.domain.ChatRoomMembers
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [DeactivateChatRoomMemberUseCase] 구현.
 * 매칭 id로 채팅방을 찾아(없으면 no-op) 그 방 참가자 중 [userIds]에 해당하는 행만 비활성([ChatRoomMembers.deactivate])으로 전이해 저장한다.
 * 팀 해체 흐름(같은 트랜잭션)에서 호출되며, 방 자체는 닫지 않는다.
 */
@Service
@Transactional
class DeactivateChatRoomMemberService(
	private val getChatRoomPort: GetChatRoomPort,
	private val getChatRoomMemberPort: GetChatRoomMemberPort,
	private val saveChatRoomMemberPort: SaveChatRoomMemberPort,
) : DeactivateChatRoomMemberUseCase {

	override fun deactivate(matchId: Long, userIds: List<Long>) {
		if (userIds.isEmpty()) return
		val chatRoom: ChatRoom = getChatRoomPort.findByMatchId(matchId) ?: return
		val members: ChatRoomMembers = getChatRoomMemberPort.findAllByChatRoomId(chatRoom.id)
		saveChatRoomMemberPort.saveAll(members.deactivate(userIds.toSet()))
	}
}
```

- [ ] **Step 3: Verify the build**

Run: `./gradlew :oneulsogae-core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/chat/command/application/port/in/DeactivateChatRoomMemberUseCase.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/chat/command/application/DeactivateChatRoomMemberService.kt
git commit -m "feat(chat): 매칭 채팅방 참가자 비활성화 유스케이스 추가"
```

---

### Task 6: 도메인 이벤트 + AlarmType + TeamEventHandler 알림 핸들러

**Files:**
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/event/OpponentsNotifiedOnDisband.kt`
- Modify: `oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/alarm/AlarmType.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/TeamEventHandler.kt`

**Interfaces:**
- Consumes: `SaveAlarmUseCase.save(SaveAlarmCommand)`, `SaveAlarmCommand(userId, type, title, description, link, fromUserId?, fromTeamId?)`
- Produces:
  - `OpponentsNotifiedOnDisband(disbandedTeamId: Long, recipientUserIds: List<Long>)` (Task 7이 발행)
  - `AlarmType.MANY_TO_MANY_OPPONENT_DISBANDED`
  - `TeamEventHandler.onOpponentsNotifiedOnDisband(event)` 핸들러

동작 검증은 Task 7 E2E. 게이트는 **빌드 통과**.

- [ ] **Step 1: Create the event**

`OpponentsNotifiedOnDisband.kt`
```kotlin
package com.org.oneulsogae.core.match.command.domain.event

/**
 * 팀 해체로 진행 중 매칭이 정리될 때, 상대 팀 활성 구성원에게 알림을 보내기 위해 발행되는 도메인 이벤트.
 * 알림은 부가 효과이므로 수신측([com.org.oneulsogae.core.match.command.application.TeamEventHandler])이 커밋 이후 처리한다.
 * [disbandedTeamId]는 해체된 팀(알림 유발 팀), [recipientUserIds]는 알림 수신자(상대 팀 활성 구성원) userId들이다.
 */
data class OpponentsNotifiedOnDisband(
	val disbandedTeamId: Long,
	val recipientUserIds: List<Long>,
)
```

- [ ] **Step 2: Add the AlarmType**

`AlarmType.kt`의 마지막 enum 상수(`TEAM_INVITATION_ACCEPTED(...)`) 뒤에 콤마를 찍고 추가:
```kotlin
	/** [팀 매칭] 상대 팀이 해체되어 매칭이 정리됨(상대 팀 구성원에게). */
	MANY_TO_MANY_OPPONENT_DISBANDED("상대 팀 해체"),
```
(즉 `TEAM_INVITATION_ACCEPTED("팀 초대 수락됨"),` 다음 줄에 위 상수를 추가)

- [ ] **Step 3: Add the handler to TeamEventHandler**

`TeamEventHandler.kt` 상단 import에 추가:
```kotlin
import com.org.oneulsogae.core.match.command.domain.event.OpponentsNotifiedOnDisband
```
클래스 마지막 핸들러(`onTeamInvitationAccepted`) 뒤, 클래스 닫는 `}` 직전에 추가:
```kotlin
	/** 팀 해체 → 상대 팀 활성 구성원 각자에게 "상대 팀 해체" 알람. (수신자 목록만큼 개별 저장) */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	fun onOpponentsNotifiedOnDisband(event: OpponentsNotifiedOnDisband) {
		event.recipientUserIds.forEach { recipientUserId: Long ->
			saveAlarmUseCase.save(
				SaveAlarmCommand(
					userId = recipientUserId,
					type = AlarmType.MANY_TO_MANY_OPPONENT_DISBANDED,
					title = "상대 팀 해체",
					description = "상대 팀이 해체되었어요.",
					link = "",
					fromTeamId = event.disbandedTeamId,
				),
			)
		}
	}
```

- [ ] **Step 4: Verify the build**

Run: `./gradlew :oneulsogae-core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/alarm/AlarmType.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/domain/event/OpponentsNotifiedOnDisband.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/TeamEventHandler.kt
git commit -m "feat(match): 팀 해체 상대 알림 이벤트·알람타입·핸들러 추가"
```

---

### Task 7: DisbandTeamService 통합 + E2E 검증

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/DisbandTeamService.kt`
- Test(Create): `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/DisbandTeamMatchTeardownE2ETest.kt`

**Interfaces:**
- Consumes: `GetTeamMatchPort.findActiveByTeamId` (Task 4), `SaveTeamMatchPort.save`, `DeactivateChatRoomMemberUseCase.deactivate` (Task 5), `DomainEventPublisher.publish`, `OpponentsNotifiedOnDisband` (Task 6), `TeamMatch.{isMatched,close,opponentTeamIdOf}` (Task 2), `Team.activeMemberIds()`
- Produces: 팀 해체 시 미성사 매칭 종료 / 성사 매칭 채팅 멤버 비활성화 / 상대 알림 발행 동작

- [ ] **Step 1: Write the failing E2E test**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/DisbandTeamMatchTeardownE2ETest.kt`
```kotlin
package com.org.oneulsogae.api.match

import com.org.oneulsogae.common.alarm.AlarmType
import com.org.oneulsogae.common.chat.ChatRoomMemberStatus
import com.org.oneulsogae.common.match.MatchStatus
import com.org.oneulsogae.common.match.MatchedTeamStatus
import com.org.oneulsogae.common.match.TeamMatchType
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.delete
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.infra.alarm.command.entity.AlarmEntity
import com.org.oneulsogae.infra.alarm.command.entity.QAlarmEntity
import com.org.oneulsogae.infra.chat.command.entity.ChatRoomMemberEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomMemberEntity
import com.org.oneulsogae.infra.fixture.ChatRoomEntityFixture
import com.org.oneulsogae.infra.fixture.ChatRoomMemberEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.MatchUserEntityFixture
import com.org.oneulsogae.infra.match.command.entity.MatchedTeamEntity
import com.org.oneulsogae.infra.match.command.entity.QMatchUserEntity
import com.org.oneulsogae.infra.match.command.entity.QMatchedTeamEntity
import com.org.oneulsogae.infra.match.command.entity.QTeamEntity
import com.org.oneulsogae.infra.match.command.entity.QTeamMemberEntity
import com.org.oneulsogae.infra.match.command.entity.TeamMatchEntity
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 팀 해체 시 매칭 정리·채팅 차단·상대 알림 E2E.
 * - 성사(MATCHED) 매칭: 그대로 유지, 나간 팀원의 chatroom_member만 DEACTIVE, 상대에게 알림
 * - 미성사(PROPOSED) 매칭: CLOSED + matched_teams DEACTIVE, 상대에게 알림
 */
class DisbandTeamMatchTeardownE2ETest : AbstractIntegrationSupport({

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

    // 두 팀을 status로 묶은 팀 매칭을 만들고 teamMatchId를 돌려준다.
    fun persistTeamMatch(myTeamId: Long, opponentTeamId: Long, status: MatchStatus): Long {
        val header: TeamMatchEntity = IntegrationUtil.persist(
            TeamMatchEntity(
                memberKey = listOf(myTeamId, opponentTeamId).sorted().joinToString("-"),
                introducedDate = LocalDate.of(2026, 6, 24),
                expiresAt = LocalDateTime.of(2026, 6, 25, 12, 0),
                status = status,
                matchType = TeamMatchType.RECOMMENDED,
                dateInitAmount = 40,
                dateAcceptAmount = 40,
            ),
        )
        val teamMatchId: Long = header.id!!
        IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = teamMatchId, teamId = myTeamId, status = MatchedTeamStatus.ACTIVE))
        IntegrationUtil.persist(MatchedTeamEntity(teamMatchId = teamMatchId, teamId = opponentTeamId, status = MatchedTeamStatus.ACTIVE))
        return teamMatchId
    }

    describe("DELETE /teams/v1/{teamId} — 매칭 정리") {

        context("성사(MATCHED) 매칭이 있는 팀을 해체하면") {
            it("매칭은 유지되고, 나간 팀원의 채팅 참가만 비활성화되며, 상대 팀원에게 알림이 간다") {
                val ownerId = 5001L
                val invitedUserId = 5002L
                val oppOwnerId = 5003L
                val oppInvitedUserId = 5004L
                val myTeamId: Long = formedTeam(ownerId, invitedUserId)
                val opponentTeamId: Long = formedTeam(oppOwnerId, oppInvitedUserId)
                val teamMatchId: Long = persistTeamMatch(myTeamId, opponentTeamId, MatchStatus.MATCHED)

                // 성사 매칭의 채팅방 + 양 팀 참가자 (matchId == teamMatchId 전제)
                val room = IntegrationUtil.persist(ChatRoomEntityFixture.create(matchId = teamMatchId))
                val roomId: Long = room.id!!
                listOf(ownerId, invitedUserId, oppOwnerId, oppInvitedUserId).forEach { uid: Long ->
                    IntegrationUtil.persist(ChatRoomMemberEntityFixture.create(chatRoomId = roomId, userId = uid))
                }

                delete("/teams/v1/$myTeamId") { bearer(accessTokenFor(invitedUserId)) } expect {
                    status(200)
                    body("success", true)
                }

                // 매칭은 그대로 MATCHED
                teamMatchStatus(teamMatchId) shouldBe MatchStatus.MATCHED
                // 나간 팀원(내 팀)만 DEACTIVE, 상대 팀원은 ACTIVE 유지
                memberStatus(roomId, ownerId) shouldBe ChatRoomMemberStatus.DEACTIVE
                memberStatus(roomId, invitedUserId) shouldBe ChatRoomMemberStatus.DEACTIVE
                memberStatus(roomId, oppOwnerId) shouldBe ChatRoomMemberStatus.ACTIVE
                memberStatus(roomId, oppInvitedUserId) shouldBe ChatRoomMemberStatus.ACTIVE
                // 상대 팀원에게 알림
                disbandAlarms(oppOwnerId).size shouldBe 1
                disbandAlarms(oppInvitedUserId).size shouldBe 1
            }
        }

        context("미성사(PROPOSED) 매칭이 있는 팀을 해체하면") {
            it("매칭이 CLOSED로 종료되고, 상대 팀원에게 알림이 간다") {
                val ownerId = 5101L
                val invitedUserId = 5102L
                val oppOwnerId = 5103L
                val oppInvitedUserId = 5104L
                val myTeamId: Long = formedTeam(ownerId, invitedUserId)
                val opponentTeamId: Long = formedTeam(oppOwnerId, oppInvitedUserId)
                val teamMatchId: Long = persistTeamMatch(myTeamId, opponentTeamId, MatchStatus.PROPOSED)

                delete("/teams/v1/$myTeamId") { bearer(accessTokenFor(invitedUserId)) } expect {
                    status(200)
                    body("success", true)
                }

                teamMatchStatus(teamMatchId) shouldBe MatchStatus.CLOSED
                disbandAlarms(oppOwnerId).size shouldBe 1
                disbandAlarms(oppInvitedUserId).size shouldBe 1
            }
        }
    }

    afterTest {
        IntegrationUtil.deleteAll(QAlarmEntity.alarmEntity)
        IntegrationUtil.deleteAll(QChatRoomMemberEntity.chatRoomMemberEntity)
        IntegrationUtil.deleteAll(QChatRoomEntity.chatRoomEntity)
        IntegrationUtil.deleteAll(QMatchedTeamEntity.matchedTeamEntity)
        IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
        IntegrationUtil.deleteAll(QTeamEntity.teamEntity)
        IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
    }
})

private fun teamMatchStatus(teamMatchId: Long): MatchStatus {
    val q = com.org.oneulsogae.infra.match.command.entity.QTeamMatchEntity.teamMatchEntity
    return IntegrationUtil.getQuery().select(q.status).from(q).where(q.id.eq(teamMatchId)).fetchOne()!!
}

private fun memberStatus(chatRoomId: Long, userId: Long): ChatRoomMemberStatus {
    val q = QChatRoomMemberEntity.chatRoomMemberEntity
    return IntegrationUtil.getQuery().select(q.status).from(q)
        .where(q.chatRoomId.eq(chatRoomId).and(q.userId.eq(userId))).fetchOne()!!
}

private fun disbandAlarms(userId: Long): List<AlarmEntity> {
    val q = QAlarmEntity.alarmEntity
    return IntegrationUtil.getQuery().selectFrom(q)
        .where(q.userId.eq(userId).and(q.type.eq(AlarmType.MANY_TO_MANY_OPPONENT_DISBANDED))).fetch()
}
```

- [ ] **Step 2: Run the E2E to verify it fails**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.match.DisbandTeamMatchTeardownE2ETest"`
Expected: FAIL — 해체 후 매칭/채팅/알림이 정리되지 않음(현재 `disband`는 매칭을 건드리지 않음). MATCHED 시나리오에서 `memberStatus(... ownerId)`가 ACTIVE로 남고, `disbandAlarms(...)`가 비어 있어 실패.

- [ ] **Step 3: Wire DisbandTeamService**

`DisbandTeamService.kt` 전체를 교체:
```kotlin
package com.org.oneulsogae.core.match.command.application

import com.org.oneulsogae.core.chat.command.application.port.`in`.DeactivateChatRoomMemberUseCase
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.common.event.DomainEventPublisher
import com.org.oneulsogae.core.common.lock.DistributedLock
import com.org.oneulsogae.core.common.lock.LockKeyConstraints
import com.org.oneulsogae.core.common.time.TimeGenerator
import com.org.oneulsogae.core.match.TeamErrorCode
import com.org.oneulsogae.core.match.command.application.port.`in`.DisbandTeamUseCase
import com.org.oneulsogae.core.match.command.application.port.out.GetTeamMatchPort
import com.org.oneulsogae.core.match.command.application.port.out.GetTeamPort
import com.org.oneulsogae.core.match.command.application.port.out.SaveTeamMatchPort
import com.org.oneulsogae.core.match.command.application.port.out.SaveTeamPort
import com.org.oneulsogae.core.match.command.domain.Team
import com.org.oneulsogae.core.match.command.domain.TeamMatch
import com.org.oneulsogae.core.match.command.domain.event.OpponentsNotifiedOnDisband
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [DisbandTeamUseCase] 구현. ACTIVE 팀을 구성원이 해체한다.
 * 팀을 [Team.disband]로 비활성화한 뒤, 그 팀이 참가한 진행 중(미종료) 팀 매칭을 상태별로 정리한다.
 * - 미성사(PROPOSED/PARTIALLY_ACCEPTED): [TeamMatch.close]로 매칭 종료(CLOSED + 참가 팀 DEACTIVE)
 * - 성사(MATCHED): 매칭은 유지하고, 나간 팀원의 채팅 참가만 비활성화([DeactivateChatRoomMemberUseCase])해 채팅방 입장을 막는다
 * 정리한 매칭들의 상대 팀 활성 구성원을 알림 수신자로 모아, 커밋 이후 알림이 가도록 [OpponentsNotifiedOnDisband]를 발행한다.
 * 모두 같은 트랜잭션에서 처리해 함께 성공/롤백된다. teamId 분산 락으로 동시 상태 변경과 직렬화한다.
 * 시각은 [TimeGenerator]로 얻어 도메인에 주입한다. (LocalDateTime.now() 직접 호출 금지)
 */
@Service
class DisbandTeamService(
	private val getTeamPort: GetTeamPort,
	private val saveTeamPort: SaveTeamPort,
	private val getTeamMatchPort: GetTeamMatchPort,
	private val saveTeamMatchPort: SaveTeamMatchPort,
	private val deactivateChatRoomMemberUseCase: DeactivateChatRoomMemberUseCase,
	private val domainEventPublisher: DomainEventPublisher,
	private val timeGenerator: TimeGenerator,
) : DisbandTeamUseCase {

	@DistributedLock(prefix = LockKeyConstraints.TEAM_LIFECYCLE, keys = ["#teamId"], waitTime = 0)
	@Transactional
	override fun disband(userId: Long, teamId: Long): Team {
		val team: Team = getTeamPort.findById(teamId)
			?: throw BusinessException(TeamErrorCode.TEAM_NOT_FOUND)
		val leavingMemberIds: List<Long> = team.activeMemberIds()
		val disbandedTeam: Team = saveTeamPort.save(team.disband(userId, timeGenerator.now()))

		val recipientUserIds: List<Long> = teardownMatches(teamId, leavingMemberIds)
		if (recipientUserIds.isNotEmpty()) {
			domainEventPublisher.publish(OpponentsNotifiedOnDisband(teamId, recipientUserIds))
		}
		return disbandedTeam
	}

	// 진행 중(미종료) 매칭을 상태별로 정리하고, 알림 대상(상대 팀 활성 구성원) userId를 중복 없이 모아 돌려준다.
	private fun teardownMatches(teamId: Long, leavingMemberIds: List<Long>): List<Long> {
		val recipientUserIds: MutableList<Long> = mutableListOf()
		getTeamMatchPort.findActiveByTeamId(teamId).forEach { teamMatch: TeamMatch ->
			val opponentTeamId: Long = teamMatch.opponentTeamIdOf(teamId)
			recipientUserIds += getTeamPort.findById(opponentTeamId)?.activeMemberIds().orEmpty()
			if (teamMatch.isMatched()) {
				deactivateChatRoomMemberUseCase.deactivate(teamMatch.id, leavingMemberIds)
			} else {
				saveTeamMatchPort.save(teamMatch.close())
			}
		}
		return recipientUserIds.distinct()
	}
}
```

- [ ] **Step 4: Run the E2E to verify it passes**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.match.DisbandTeamMatchTeardownE2ETest"`
Expected: PASS (두 시나리오 모두)

- [ ] **Step 5: Run the existing disband E2E to confirm no regression**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.match.DisbandTeamE2ETest"`
Expected: PASS (매칭 없는 팀 해체는 종전대로 동작)

- [ ] **Step 6: Commit**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/match/command/application/DisbandTeamService.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/match/DisbandTeamMatchTeardownE2ETest.kt
git commit -m "feat(match): 팀 해체 시 매칭 종료·채팅 차단·상대 알림 처리"
```

---

## 최종 검증

- [ ] **전체 테스트 실행**

Run: `./gradlew :oneulsogae-api:test`
Expected: BUILD SUCCESSFUL (신규 + 기존 테스트 전부 통과)
