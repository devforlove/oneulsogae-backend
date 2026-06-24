# 팀 결성 시 추천 팀 → TeamMatch 승격 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 팀 초대 수락으로 팀 T가 결성(ACTIVE)될 때, 각 구성원에게 개인 추천됐던 팀(recommended_teams)을 결성된 팀 T와의 `TeamMatch`(PROPOSED)로 승격한다.

**Architecture:** 헥사고날. `AcceptTeamInvitationService.accept()`(같은 트랜잭션·teamId 분산 락) 안에서 동기 처리. 솔로 `Match` 애그리거트를 미러링해 `TeamMatch` 도메인 모델·out-port·infra 어댑터를 신규 추가한다. 본 작업은 `TeamMatch`를 생성하는 최초 경로다(기존엔 엔티티만 존재).

**Tech Stack:** Kotlin 2.2.21 / JVM 21, Spring Boot 4.0.6, Spring Data JPA, Kotest(DescribeSpec) 단위 테스트, Testcontainers + RestAssured E2E.

## Global Constraints

- 멀티모듈 의존 방향: `common`(enum) → `core`(도메인/유스케이스/out-port) → `infra`(엔티티/어댑터/리포지토리/매퍼). 도메인 모델은 `meeple-core`, 엔티티/어댑터는 `meeple-infra`, enum은 `meeple-common`.
- CQS: 본 작업은 명령 경로. `GetRecommendedTeamPort`는 명령 트랜잭션 내 보조 단건 조회(잠금·상태변경 없음)로 `command/.../port/out`에 둔다.
- 엔티티당 어댑터 하나: `recommended_teams`는 기존 `RecommendedTeamAdapter` 한 곳에서 scheduler의 `SaveRecommendedTeamPort` + core의 `GetRecommendedTeamPort`를 함께 구현한다.
- 현재 시각은 `LocalDateTime.now()` 직접 호출 금지 — `TimeGenerator.now()`로 얻어 도메인엔 `now` 파라미터로 주입.
- 일급 컬렉션: 참가 팀 목록은 `MatchedTeams`로 래핑.
- 타입 명시: 변수·반환·람다 파라미터 타입을 생략하지 않는다.
- 도메인 유닛 테스트는 `meeple-api/src/test/.../domain/match/` 패키지에 둔다(meeple-core엔 test source set 없음). E2E는 `meeple-api/src/test/.../api/match/`.
- TeamMatch 필드 확정값: `status=PROPOSED`, 두 팀 `accepted=null`, `matchType=RECOMMENDED`(신규), `dateInitAmount=MEETING_INIT(40)`, `dateAcceptAmount=MEETING_ACCEPT(40)`, `expiresAt=now+Duration.ofDays(1)`, `memberKey=정렬된 teamId join("-")`.

---

### Task 1: TeamMatch 도메인 모델 + RECOMMENDED 타입

**Files:**
- Modify: `meeple-common/src/main/kotlin/com/org/meeple/common/match/TeamMatchType.kt`
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/match/command/domain/MatchedTeam.kt`
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/match/command/domain/MatchedTeams.kt`
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/match/command/domain/TeamMatch.kt`
- Test: `meeple-api/src/test/kotlin/com/org/meeple/domain/match/TeamMatchTest.kt`

**Interfaces:**
- Consumes: `CoinUsageType.MEETING_INIT.coinAmount`(40), `CoinUsageType.MEETING_ACCEPT.coinAmount`(40), `MatchStatus.PROPOSED`, `TeamMatchType`.
- Produces:
  - `TeamMatch.propose(teamAId: Long, teamBId: Long, matchType: TeamMatchType, now: LocalDateTime): TeamMatch`
  - `TeamMatch.memberKey(): String`, fields `id, matchedTeams, introducedDate, expiresAt, matchType, status, dateInitAmount, dateAcceptAmount, deletedAt`
  - `MatchedTeams.of(teamIds: List<Long>): MatchedTeams`, `MatchedTeams.teamIds(): List<Long>`, `MatchedTeams.memberKey(): String`, field `values: List<MatchedTeam>`
  - `MatchedTeam(id, teamMatchId, teamId, accepted, deletedAt)` (data class)
  - `TeamMatchType.RECOMMENDED`

- [ ] **Step 1: `TeamMatchType`에 RECOMMENDED 값 추가**

`meeple-common/src/main/kotlin/com/org/meeple/common/match/TeamMatchType.kt`의 `REQUIRED` 항목 뒤에 추가:

```kotlin
package com.org.meeple.common.match

/** 2:2(팀) 매칭이 생성된 경로(유형). (온보딩 자동 소개는 1:1만 있으므로 팀 유형엔 없다) */
enum class TeamMatchType(val description: String) {

	/** 일일 매칭 배치로 생성된 팀 매칭. */
	DAILY("일일 팀 매칭"),

	/** 사용자 요청(필수 신청)으로 생성된 팀 매칭. */
	REQUIRED("요청 팀 매칭"),

	/** 팀 결성 시 구성원 개인 추천(recommended_teams)을 결성된 팀의 매칭으로 승격해 생성된 팀 매칭. */
	RECOMMENDED("추천 팀 매칭"),
}
```

- [ ] **Step 2: `MatchedTeam` 도메인 모델 작성**

Create `meeple-core/src/main/kotlin/com/org/meeple/core/match/command/domain/MatchedTeam.kt`:

```kotlin
package com.org.meeple.core.match.command.domain

import java.time.LocalDateTime

/**
 * 팀 매칭([TeamMatch])에 참가한 한 팀을 (teamMatchId, teamId) 한 쌍으로 나타내는 도메인 모델.
 * 매치별 수락 여부([accepted])를 팀이 아니라 이 모델이 보관한다. (응답 전이면 null)
 */
data class MatchedTeam(
	val id: Long = 0,
	val teamMatchId: Long,
	val teamId: Long,
	val accepted: Boolean? = null,
	val deletedAt: LocalDateTime? = null,
)
```

- [ ] **Step 3: `MatchedTeams` 일급 컬렉션 작성**

Create `meeple-core/src/main/kotlin/com/org/meeple/core/match/command/domain/MatchedTeams.kt`:

```kotlin
package com.org.meeple.core.match.command.domain

/**
 * 한 팀 매칭([TeamMatch])에 참가한 팀([MatchedTeam]) 목록의 일급 컬렉션.
 * 참가 팀 식별과, 재소개 방지에 쓰는 멤버 키(정렬된 team-id 결합) 산출을 한곳에 응집한다.
 */
data class MatchedTeams(
	val values: List<MatchedTeam>,
) {

	/** 참가 팀 수. */
	val size: Int
		get() = values.size

	/** 참가 팀 id 목록. */
	fun teamIds(): List<Long> =
		values.map { matchedTeam: MatchedTeam -> matchedTeam.teamId }

	/** 두 팀 조합을 식별하는 정규화 키. (순서와 무관하게 같은 조합이면 같은 키) */
	fun memberKey(): String =
		teamIds().sorted().joinToString("-")

	companion object {

		/** teamId들로 참가 팀 목록을 만든다. (teamMatchId는 저장 시 채워진다) */
		fun of(teamIds: List<Long>): MatchedTeams =
			MatchedTeams(
				teamIds.map { teamId: Long -> MatchedTeam(teamMatchId = 0, teamId = teamId) },
			)
	}
}
```

- [ ] **Step 4: `TeamMatch` 도메인 모델 작성**

Create `meeple-core/src/main/kotlin/com/org/meeple/core/match/command/domain/TeamMatch.kt`:

```kotlin
package com.org.meeple.core.match.command.domain

import com.org.meeple.common.coin.CoinUsageType
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.TeamMatchType
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 2:2(팀) 매칭 애그리거트의 도메인 모델. 독립적으로 결성된 두 팀을 소개로 묶는다.
 * 참가 팀(과 매치별 수락 여부)은 [matchedTeams]가 보관한다.
 * 두 팀 조합의 정규화 키([memberKey])로 같은 조합의 중복 소개를 차단한다. (재소개 방지)
 */
data class TeamMatch(
	val id: Long = 0,
	val matchedTeams: MatchedTeams,
	val introducedDate: LocalDate,
	val expiresAt: LocalDateTime,
	val matchType: TeamMatchType,
	val status: MatchStatus = MatchStatus.PROPOSED,
	val dateInitAmount: Int = CoinUsageType.MEETING_INIT.coinAmount,
	val dateAcceptAmount: Int = CoinUsageType.MEETING_ACCEPT.coinAmount,
	val deletedAt: LocalDateTime? = null,
) {

	/** 참가 팀 조합을 식별하는 정규화 키. (재소개 방지 유니크 키) */
	fun memberKey(): String =
		matchedTeams.memberKey()

	companion object {

		/** 팀 매칭의 유효 기간. 생성 시각으로부터 이 기간이 지나면 만료된 것으로 본다. */
		val EXPIRATION: Duration = Duration.ofDays(1)

		/**
		 * 두 팀([teamAId], [teamBId])을 참가 팀으로 하는 신규 팀 매칭을 생성한다. (status PROPOSED, 양쪽 accepted=null)
		 * 소개 일자(introducedDate)는 [now]의 날짜, 만료 시각(expiresAt)은 [now] + [EXPIRATION]으로 설정한다.
		 * 팀 매칭 신청/수락 코인 비용은 [CoinUsageType]에서 가져오고, 생성 경로는 [matchType]으로 기록한다.
		 */
		fun propose(teamAId: Long, teamBId: Long, matchType: TeamMatchType, now: LocalDateTime): TeamMatch =
			TeamMatch(
				matchedTeams = MatchedTeams.of(listOf(teamAId, teamBId)),
				introducedDate = now.toLocalDate(),
				expiresAt = now.plus(EXPIRATION),
				matchType = matchType,
			)
	}
}
```

- [ ] **Step 5: 실패하는 단위 테스트 작성**

Create `meeple-api/src/test/kotlin/com/org/meeple/domain/match/TeamMatchTest.kt`:

```kotlin
package com.org.meeple.domain.match

import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.TeamMatchType
import com.org.meeple.core.match.command.domain.TeamMatch
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

/**
 * [TeamMatch] 도메인 유닛 테스트.
 * 프레임워크·인프라 없이 순수 도메인 로직(팀 매칭 생성, 멤버 키 산출)을 검증한다.
 */
class TeamMatchTest : DescribeSpec({

	val now: LocalDateTime = LocalDateTime.of(2026, 6, 24, 12, 0)

	describe("propose - 팀 매칭 생성") {
		it("두 팀을 참가 팀으로 담아 PROPOSED 상태의 팀 매칭을 생성한다") {
			val teamMatch: TeamMatch = TeamMatch.propose(
				teamAId = 10L,
				teamBId = 20L,
				matchType = TeamMatchType.RECOMMENDED,
				now = now,
			)

			teamMatch.status shouldBe MatchStatus.PROPOSED
			teamMatch.matchType shouldBe TeamMatchType.RECOMMENDED
			teamMatch.introducedDate shouldBe now.toLocalDate()
			teamMatch.expiresAt shouldBe now.plusDays(1)
			teamMatch.dateInitAmount shouldBe 40
			teamMatch.dateAcceptAmount shouldBe 40
			teamMatch.matchedTeams.teamIds() shouldBe listOf(10L, 20L)
			teamMatch.matchedTeams.values.all { it.accepted == null } shouldBe true
		}

		it("memberKey는 두 teamId를 정렬해 '-'로 잇는다 (순서 무관)") {
			val teamMatch: TeamMatch = TeamMatch.propose(
				teamAId = 20L,
				teamBId = 10L,
				matchType = TeamMatchType.RECOMMENDED,
				now = now,
			)

			teamMatch.memberKey() shouldBe "10-20"
		}
	}
})
```

- [ ] **Step 6: 테스트 실패 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.domain.match.TeamMatchTest"`
Expected: 컴파일 통과 후 (Step 2~4 구현이 이미 있으므로) PASS. 만약 Step 2~4를 건너뛰고 테스트만 먼저 작성했다면 컴파일 에러("Unresolved reference: TeamMatch")로 FAIL.

> 참고: 이 프로젝트는 도메인 구현과 테스트를 같은 작업에서 함께 만든다. TDD 순서를 엄격히 보려면 Step 5를 Step 2 앞으로 옮겨 컴파일 실패를 먼저 확인한 뒤 구현해도 된다.

- [ ] **Step 7: 테스트 통과 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.domain.match.TeamMatchTest"`
Expected: PASS (2 tests)

- [ ] **Step 8: 커밋**

```bash
git add meeple-common/src/main/kotlin/com/org/meeple/common/match/TeamMatchType.kt \
        meeple-core/src/main/kotlin/com/org/meeple/core/match/command/domain/MatchedTeam.kt \
        meeple-core/src/main/kotlin/com/org/meeple/core/match/command/domain/MatchedTeams.kt \
        meeple-core/src/main/kotlin/com/org/meeple/core/match/command/domain/TeamMatch.kt \
        meeple-api/src/test/kotlin/com/org/meeple/domain/match/TeamMatchTest.kt
git commit -m "feat(match): TeamMatch 도메인 모델과 RECOMMENDED 매칭 타입 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: out-port + infra 영속성 (TeamMatch 저장 / 추천 팀 조회)

**Files:**
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/match/command/application/port/out/GetRecommendedTeamPort.kt`
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/match/command/application/port/out/SaveTeamMatchPort.kt`
- Create: `meeple-infra/src/main/kotlin/com/org/meeple/infra/match/command/repository/TeamMatchJpaRepository.kt`
- Create: `meeple-infra/src/main/kotlin/com/org/meeple/infra/match/command/repository/MatchedTeamJpaRepository.kt`
- Create: `meeple-infra/src/main/kotlin/com/org/meeple/infra/match/command/mapper/TeamMatchMapper.kt`
- Create: `meeple-infra/src/main/kotlin/com/org/meeple/infra/match/command/mapper/MatchedTeamMapper.kt`
- Create: `meeple-infra/src/main/kotlin/com/org/meeple/infra/match/command/adapter/TeamMatchAdapter.kt`
- Modify: `meeple-infra/src/main/kotlin/com/org/meeple/infra/match/command/adapter/RecommendedTeamAdapter.kt`

**Interfaces:**
- Consumes (from Task 1): `TeamMatch`, `MatchedTeams`, `MatchedTeam`, `TeamMatch.memberKey()`. 기존 엔티티 `TeamMatchEntity(memberKey, introducedDate, expiresAt, status, matchType, dateInitAmount, dateAcceptAmount)`, `MatchedTeamEntity(teamMatchId, teamId, accepted)`, `BaseEntity.id: Long?`, `BaseEntity.softDelete(at)`, `RecommendedTeamJpaRepository.findByUserId(userId): RecommendedTeamEntity?`.
- Produces (for Task 3):
  - `GetRecommendedTeamPort { fun findRecommendedTeamId(userId: Long): Long? }`
  - `SaveTeamMatchPort { fun save(teamMatch: TeamMatch): TeamMatch }`

> 참고: infra 모듈은 별도 src/test가 없어(어댑터는 E2E로 검증) 본 태스크의 검증은 **컴파일 성공**이며, 런타임 동작은 Task 3의 E2E가 검증한다.

- [ ] **Step 1: `GetRecommendedTeamPort` 작성**

Create `meeple-core/src/main/kotlin/com/org/meeple/core/match/command/application/port/out/GetRecommendedTeamPort.kt`:

```kotlin
package com.org.meeple.core.match.command.application.port.out

/**
 * 추천 팀(recommended_teams) 단건 조회 포트. (명령 트랜잭션 내 보조 조회 — 잠금/상태변경 없음)
 * 팀 결성 시 각 구성원에게 추천됐던 팀 id를 읽어 팀 매칭으로 승격하는 데 쓴다.
 */
interface GetRecommendedTeamPort {

	/** [userId]에게 추천된 팀 id. 추천이 없으면 null. */
	fun findRecommendedTeamId(userId: Long): Long?
}
```

- [ ] **Step 2: `SaveTeamMatchPort` 작성**

Create `meeple-core/src/main/kotlin/com/org/meeple/core/match/command/application/port/out/SaveTeamMatchPort.kt`:

```kotlin
package com.org.meeple.core.match.command.application.port.out

import com.org.meeple.core.match.command.domain.TeamMatch

/** 팀 매칭 애그리거트(헤더 + 참가 팀) 저장 포트. */
interface SaveTeamMatchPort {

	fun save(teamMatch: TeamMatch): TeamMatch
}
```

- [ ] **Step 3: 리포지토리 2개 작성**

Create `meeple-infra/src/main/kotlin/com/org/meeple/infra/match/command/repository/TeamMatchJpaRepository.kt`:

```kotlin
package com.org.meeple.infra.match.command.repository

import com.org.meeple.infra.match.command.entity.TeamMatchEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 팀 매칭 헤더(team_matches) 리포지토리.
 * [com.org.meeple.infra.match.command.adapter.TeamMatchAdapter]가 헤더 저장에 사용한다.
 */
interface TeamMatchJpaRepository : JpaRepository<TeamMatchEntity, Long>
```

Create `meeple-infra/src/main/kotlin/com/org/meeple/infra/match/command/repository/MatchedTeamJpaRepository.kt`:

```kotlin
package com.org.meeple.infra.match.command.repository

import com.org.meeple.infra.match.command.entity.MatchedTeamEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 팀 매칭 참가 팀(matched_teams) 리포지토리.
 * [com.org.meeple.infra.match.command.adapter.TeamMatchAdapter]가 참가 팀 행 저장에 사용한다.
 */
interface MatchedTeamJpaRepository : JpaRepository<MatchedTeamEntity, Long>
```

- [ ] **Step 4: 매퍼 2개 작성**

Create `meeple-infra/src/main/kotlin/com/org/meeple/infra/match/command/mapper/TeamMatchMapper.kt`:

```kotlin
package com.org.meeple.infra.match.command.mapper

import com.org.meeple.core.match.command.domain.MatchedTeams
import com.org.meeple.core.match.command.domain.TeamMatch
import com.org.meeple.infra.match.command.entity.TeamMatchEntity
import java.time.LocalDateTime

/** 영속성 엔티티 -> 도메인 모델. 참가 팀([MatchedTeams])은 어댑터가 조회해 함께 넘긴다. */
fun TeamMatchEntity.toDomain(matchedTeams: MatchedTeams): TeamMatch =
	TeamMatch(
		id = id ?: 0,
		matchedTeams = matchedTeams,
		introducedDate = introducedDate,
		expiresAt = expiresAt,
		matchType = matchType,
		status = status,
		dateInitAmount = dateInitAmount,
		dateAcceptAmount = dateAcceptAmount,
		deletedAt = deletedAt,
	)

/**
 * 도메인 모델 -> 영속성 엔티티(헤더). 참가 팀 조합 키는 [TeamMatch.memberKey]에서 산출한다.
 * id가 0이면 신규 저장(INSERT), 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 * deletedAt이 있으면 소프트 삭제 상태로 마킹한다.
 */
fun TeamMatch.toEntity(): TeamMatchEntity =
	TeamMatchEntity(
		memberKey = memberKey(),
		introducedDate = introducedDate,
		expiresAt = expiresAt,
		status = status,
		matchType = matchType,
		dateInitAmount = dateInitAmount,
		dateAcceptAmount = dateAcceptAmount,
	).also {
		if (id != 0L) it.id = id
		deletedAt?.let { at: LocalDateTime -> it.softDelete(at) }
	}
```

Create `meeple-infra/src/main/kotlin/com/org/meeple/infra/match/command/mapper/MatchedTeamMapper.kt`:

```kotlin
package com.org.meeple.infra.match.command.mapper

import com.org.meeple.core.match.command.domain.MatchedTeam
import com.org.meeple.infra.match.command.entity.MatchedTeamEntity
import java.time.LocalDateTime

/** 영속성 엔티티 -> 도메인 모델. */
fun MatchedTeamEntity.toDomain(): MatchedTeam =
	MatchedTeam(
		id = id ?: 0,
		teamMatchId = teamMatchId,
		teamId = teamId,
		accepted = accepted,
		deletedAt = deletedAt,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규 저장(INSERT), 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 * deletedAt이 있으면 소프트 삭제 상태로 마킹한다.
 */
fun MatchedTeam.toEntity(): MatchedTeamEntity =
	MatchedTeamEntity(
		teamMatchId = teamMatchId,
		teamId = teamId,
		accepted = accepted,
	).also {
		if (id != 0L) it.id = id
		deletedAt?.let { at: LocalDateTime -> it.softDelete(at) }
	}
```

- [ ] **Step 5: `TeamMatchAdapter` 작성**

Create `meeple-infra/src/main/kotlin/com/org/meeple/infra/match/command/adapter/TeamMatchAdapter.kt`:

```kotlin
package com.org.meeple.infra.match.command.adapter

import com.org.meeple.core.match.command.application.port.out.SaveTeamMatchPort
import com.org.meeple.core.match.command.domain.MatchedTeams
import com.org.meeple.core.match.command.domain.TeamMatch
import com.org.meeple.infra.match.command.entity.TeamMatchEntity
import com.org.meeple.infra.match.command.mapper.toDomain
import com.org.meeple.infra.match.command.mapper.toEntity
import com.org.meeple.infra.match.command.repository.MatchedTeamJpaRepository
import com.org.meeple.infra.match.command.repository.TeamMatchJpaRepository
import org.springframework.stereotype.Component

/**
 * [TeamMatchEntity]의 command 영속성 어댑터. ([SaveTeamMatchPort] 구현)
 * 팀 매칭은 헤더(team_matches) + 참가 팀(matched_teams)으로 이뤄진 하나의 애그리거트이므로,
 * 이 어댑터가 두 테이블의 영속화를 함께 책임진다. (헤더 저장 → id 획득 → 그 id로 참가 팀 행 저장)
 */
@Component
class TeamMatchAdapter(
	private val teamMatchJpaRepository: TeamMatchJpaRepository,
	private val matchedTeamJpaRepository: MatchedTeamJpaRepository,
) : SaveTeamMatchPort {

	override fun save(teamMatch: TeamMatch): TeamMatch {
		val savedEntity: TeamMatchEntity = teamMatchJpaRepository.save(teamMatch.toEntity())
		val teamMatchId: Long = savedEntity.id!!
		val savedMatchedTeams: MatchedTeams = MatchedTeams(
			matchedTeamJpaRepository
				.saveAll(teamMatch.matchedTeams.values.map { it.copy(teamMatchId = teamMatchId).toEntity() })
				.map { it.toDomain() },
		)
		return savedEntity.toDomain(savedMatchedTeams)
	}
}
```

- [ ] **Step 6: `RecommendedTeamAdapter`에 `GetRecommendedTeamPort` 구현 추가**

Modify `meeple-infra/src/main/kotlin/com/org/meeple/infra/match/command/adapter/RecommendedTeamAdapter.kt`. import 추가, 구현 인터페이스 추가, 메서드 추가. 전체 파일:

```kotlin
package com.org.meeple.infra.match.command.adapter

import com.org.meeple.core.match.command.application.port.out.GetRecommendedTeamPort
import com.org.meeple.infra.match.command.entity.RecommendedTeamEntity
import com.org.meeple.infra.match.command.repository.RecommendedTeamJpaRepository
import com.org.meeple.scheduler.match.command.application.port.out.SaveRecommendedTeamPort
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * [RecommendedTeamEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나)
 * scheduler의 추천 적재 out-port([SaveRecommendedTeamPort])와 core의 추천 단건 조회 out-port([GetRecommendedTeamPort])를 함께 구현한다.
 * user_id 기준 upsert: 기존 행이 있으면 team_id·추천 일자만 갱신(UPDATE), 없으면 새 행 INSERT. (유저당 1행 = 주기마다 교체)
 */
@Component
class RecommendedTeamAdapter(
	private val recommendedTeamJpaRepository: RecommendedTeamJpaRepository,
) : SaveRecommendedTeamPort, GetRecommendedTeamPort {

	override fun findRecommendedTeamId(userId: Long): Long? =
		recommendedTeamJpaRepository.findByUserId(userId)?.teamId

	override fun replace(userId: Long, teamId: Long, recommendedDate: LocalDate) {
		val entity: RecommendedTeamEntity = recommendedTeamJpaRepository.findByUserId(userId)
			?.also {
				it.teamId = teamId
				it.recommendedDate = recommendedDate
			}
			?: RecommendedTeamEntity(userId = userId, teamId = teamId, recommendedDate = recommendedDate)
		recommendedTeamJpaRepository.save(entity)
	}
}
```

- [ ] **Step 7: 컴파일 확인**

Run: `./gradlew :meeple-infra:compileKotlin`
Expected: BUILD SUCCESSFUL (core·infra 컴파일 통과)

- [ ] **Step 8: 커밋**

```bash
git add meeple-core/src/main/kotlin/com/org/meeple/core/match/command/application/port/out/GetRecommendedTeamPort.kt \
        meeple-core/src/main/kotlin/com/org/meeple/core/match/command/application/port/out/SaveTeamMatchPort.kt \
        meeple-infra/src/main/kotlin/com/org/meeple/infra/match/command/repository/TeamMatchJpaRepository.kt \
        meeple-infra/src/main/kotlin/com/org/meeple/infra/match/command/repository/MatchedTeamJpaRepository.kt \
        meeple-infra/src/main/kotlin/com/org/meeple/infra/match/command/mapper/TeamMatchMapper.kt \
        meeple-infra/src/main/kotlin/com/org/meeple/infra/match/command/mapper/MatchedTeamMapper.kt \
        meeple-infra/src/main/kotlin/com/org/meeple/infra/match/command/adapter/TeamMatchAdapter.kt \
        meeple-infra/src/main/kotlin/com/org/meeple/infra/match/command/adapter/RecommendedTeamAdapter.kt
git commit -m "feat(match): TeamMatch 저장·추천 팀 조회 out-port와 영속성 어댑터 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: accept() 승격 로직 + E2E 검증

**Files:**
- Modify: `meeple-core/src/main/kotlin/com/org/meeple/core/match/command/application/AcceptTeamInvitationService.kt`
- Test: `meeple-api/src/test/kotlin/com/org/meeple/api/match/TeamMatchPromotionOnAcceptE2ETest.kt`

**Interfaces:**
- Consumes (from Task 1·2): `TeamMatch.propose(...)`, `TeamMatchType.RECOMMENDED`, `GetRecommendedTeamPort.findRecommendedTeamId(userId)`, `SaveTeamMatchPort.save(teamMatch)`, 기존 `GetTeamPort.findById`, `Team`, `TeamMember`, `TeamMemberStatus.ACTIVE`, `TeamStatus.ACTIVE`.
- Produces: 동작 — 팀이 ACTIVE로 결성되면 구성원 추천 팀(ACTIVE)을 PROPOSED `TeamMatch`로 승격.

- [ ] **Step 1: 서비스에 승격 로직 추가**

Modify `meeple-core/src/main/kotlin/com/org/meeple/core/match/command/application/AcceptTeamInvitationService.kt`. import 블록에 다음을 추가:

```kotlin
import com.org.meeple.common.match.TeamMatchType
import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.core.match.command.application.port.out.GetRecommendedTeamPort
import com.org.meeple.core.match.command.application.port.out.SaveTeamMatchPort
import com.org.meeple.core.match.command.domain.TeamMatch
import com.org.meeple.core.match.command.domain.TeamMember
```

생성자에 포트 2개 주입 (timeGenerator·domainEventPublisher 앞에 추가):

```kotlin
@Service
class AcceptTeamInvitationService(
	private val getTeamPort: GetTeamPort,
	private val saveTeamPort: SaveTeamPort,
	private val getRecommendedTeamPort: GetRecommendedTeamPort,
	private val saveTeamMatchPort: SaveTeamMatchPort,
	private val timeGenerator: TimeGenerator,
	private val domainEventPublisher: DomainEventPublisher,
) : AcceptTeamInvitationUseCase {
```

`accept()` 본문에서 `deactivateOtherInvitations(...)` 다음, `domainEventPublisher.publish(...)` 앞에 승격 호출 추가:

```kotlin
	@DistributedLock(prefix = LockKeyConstraints.TEAM_LIFECYCLE, keys = ["#teamId"], waitTime = 0)
	@Transactional
	override fun accept(userId: Long, teamId: Long): Team {
		val team: Team = getTeamPort.findById(teamId)
			?: throw BusinessException(TeamErrorCode.TEAM_NOT_FOUND)
		val accepted: Team = saveTeamPort.save(team.acceptInvitation(userId))
		deactivateOtherInvitations(userId, teamId)
		// 전원 수락으로 팀이 결성(ACTIVE)되면, 구성원 개인 추천(recommended_teams)을 결성된 팀의 팀 매칭으로 승격한다.
		if (accepted.status == TeamStatus.ACTIVE) {
			promoteRecommendedTeams(accepted, timeGenerator.now())
		}
		// 초대받은 사람이 수락 → 초대했던 사람에게 알람이 가도록 이벤트 발행. (커밋 이후 핸들러가 처리)
		// 초대자는 수락 직전(INVITING — 유일한 ACTIVE) 팀에서 읽는다. 수락 후엔 전원 ACTIVE라 초대자/수락자를 구분할 수 없다.
		domainEventPublisher.publish(
			TeamInvitationAccepted(accepted.id, inviterUserId = team.inviterId(), invitedUserId = userId),
		)
		return accepted
	}
```

클래스 마지막 private 메서드로 승격 로직 추가:

```kotlin
	/**
	 * 팀이 결성(ACTIVE)되면, 구성원들에게 개인 추천됐던 팀(recommended_teams)을 결성된 팀 [team]과의 팀 매칭으로 승격한다.
	 * 추천 팀이 여전히 ACTIVE일 때만 PROPOSED 팀 매칭을 생성한다. (추천 없는 구성원·해체된 추천 팀은 스킵)
	 * 두 구성원이 같은 팀을 추천받았으면 distinct로 한 번만 승격한다. (member_key 유니크 충돌 방지)
	 */
	private fun promoteRecommendedTeams(team: Team, now: LocalDateTime) {
		val memberIds: List<Long> = team.members.values
			.filter { member: TeamMember -> member.status == TeamMemberStatus.ACTIVE }
			.map { member: TeamMember -> member.userId }
		val recommendedTeamIds: List<Long> = memberIds
			.mapNotNull { memberId: Long -> getRecommendedTeamPort.findRecommendedTeamId(memberId) }
			.distinct()
			.filter { recommendedTeamId: Long -> recommendedTeamId != team.id }
		recommendedTeamIds.forEach { recommendedTeamId: Long ->
			val recommended: Team? = getTeamPort.findById(recommendedTeamId)
			if (recommended != null && recommended.status == TeamStatus.ACTIVE) {
				saveTeamMatchPort.save(
					TeamMatch.propose(team.id, recommendedTeamId, TeamMatchType.RECOMMENDED, now),
				)
			}
		}
	}
```

- [ ] **Step 2: 실패하는 E2E 테스트 작성**

Create `meeple-api/src/test/kotlin/com/org/meeple/api/match/TeamMatchPromotionOnAcceptE2ETest.kt`:

```kotlin
package com.org.meeple.api.match

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.post
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.TeamMatchType
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.alarm.command.entity.QAlarmEntity
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.fixture.RecommendedTeamEntityFixture
import com.org.meeple.infra.match.command.entity.MatchedTeamEntity
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QMatchedTeamEntity
import com.org.meeple.infra.match.command.entity.QRecommendedTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMatchEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.infra.match.command.entity.TeamEntity
import com.org.meeple.infra.match.command.entity.TeamMatchEntity
import io.kotest.matchers.shouldBe

/**
 * `POST /teams/v1/{teamId}/acceptance` 수락으로 팀이 결성(ACTIVE)될 때,
 * 두 구성원에게 개인 추천됐던 팀(recommended_teams)이 결성된 팀과의 PROPOSED 팀 매칭으로 승격되는지 검증한다.
 */
class TeamMatchPromotionOnAcceptE2ETest : AbstractIntegrationSupport({

	fun persistMatchUser(userId: Long, gender: Gender) {
		IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = gender))
	}

	fun persistFemaleTeam(status: TeamStatus): Long {
		val team: TeamEntity = IntegrationUtil.persist(
			TeamEntity(name = "여성팀", gender = Gender.FEMALE, regionId = 1L, introduction = "즐겁게 만나요", status = status),
		)
		return team.id!!
	}

	fun persistRecommendation(userId: Long, teamId: Long) {
		IntegrationUtil.persist(RecommendedTeamEntityFixture.create(userId = userId, teamId = teamId))
	}

	// 같은 성별 두 솔로 유저로 팀을 결성(초대→수락)하고 teamId를 돌려준다. 추천 시드 후 호출해야 수락 시 승격이 일어난다.
	fun inviteTeam(ownerId: Long, invitedUserId: Long): Long =
		post("/teams/v1/invitation") {
			bearer(accessTokenFor(ownerId))
			jsonBody("""{"invitedUserId": $invitedUserId, "regionId": 1, "name": "우리팀", "introduction": "함께 즐겁게 활동할 팀이에요"}""")
		}.extract().path<Int>("data.teamId").toLong()

	fun acceptTeam(invitedUserId: Long, teamId: Long) {
		post("/teams/v1/$teamId/acceptance") {
			bearer(accessTokenFor(invitedUserId))
		} expect {
			status(200)
		}
	}

	describe("POST /teams/v1/{teamId}/acceptance — 추천 팀 승격") {

		context("두 구성원 모두 ACTIVE 추천 팀이 있으면") {
			it("각 추천 팀이 PROPOSED·RECOMMENDED 팀 매칭으로 승격된다 (2건, 양쪽 accepted=null)") {
				val ownerId = 3001L
				val invitedUserId = 3002L
				persistMatchUser(ownerId, Gender.MALE)
				persistMatchUser(invitedUserId, Gender.MALE)
				val recTeamForOwner: Long = persistFemaleTeam(TeamStatus.ACTIVE)
				val recTeamForInvited: Long = persistFemaleTeam(TeamStatus.ACTIVE)
				persistRecommendation(ownerId, recTeamForOwner)
				persistRecommendation(invitedUserId, recTeamForInvited)

				val teamId: Long = inviteTeam(ownerId, invitedUserId)
				acceptTeam(invitedUserId, teamId)

				val teamMatches: List<TeamMatchEntity> = allTeamMatches()
				teamMatches.size shouldBe 2
				teamMatches.all { it.status == MatchStatus.PROPOSED } shouldBe true
				teamMatches.all { it.matchType == TeamMatchType.RECOMMENDED } shouldBe true
				teamMatches.map { it.memberKey }.toSet() shouldBe setOf(
					listOf(teamId, recTeamForOwner).sorted().joinToString("-"),
					listOf(teamId, recTeamForInvited).sorted().joinToString("-"),
				)
				teamMatches.forEach { teamMatch: TeamMatchEntity ->
					val matched: List<MatchedTeamEntity> = matchedTeamsOf(teamMatch.id!!)
					matched.size shouldBe 2
					matched.all { it.accepted == null } shouldBe true
				}
			}
		}

		context("한 구성원에게만 추천 팀이 있으면") {
			it("팀 매칭이 1건만 생성된다") {
				val ownerId = 3011L
				val invitedUserId = 3012L
				persistMatchUser(ownerId, Gender.MALE)
				persistMatchUser(invitedUserId, Gender.MALE)
				val recTeam: Long = persistFemaleTeam(TeamStatus.ACTIVE)
				persistRecommendation(ownerId, recTeam)

				val teamId: Long = inviteTeam(ownerId, invitedUserId)
				acceptTeam(invitedUserId, teamId)

				val teamMatches: List<TeamMatchEntity> = allTeamMatches()
				teamMatches.size shouldBe 1
				teamMatches[0].memberKey shouldBe listOf(teamId, recTeam).sorted().joinToString("-")
			}
		}

		context("추천 팀이 ACTIVE가 아니면") {
			it("승격하지 않는다 (0건)") {
				val ownerId = 3021L
				val invitedUserId = 3022L
				persistMatchUser(ownerId, Gender.MALE)
				persistMatchUser(invitedUserId, Gender.MALE)
				val deactivatedTeam: Long = persistFemaleTeam(TeamStatus.DEACTIVATED)
				persistRecommendation(ownerId, deactivatedTeam)

				val teamId: Long = inviteTeam(ownerId, invitedUserId)
				acceptTeam(invitedUserId, teamId)

				allTeamMatches().size shouldBe 0
			}
		}

		context("두 구성원이 같은 팀을 추천받으면") {
			it("팀 매칭은 1건만 생성된다 (중복 제거)") {
				val ownerId = 3031L
				val invitedUserId = 3032L
				persistMatchUser(ownerId, Gender.MALE)
				persistMatchUser(invitedUserId, Gender.MALE)
				val sharedRecTeam: Long = persistFemaleTeam(TeamStatus.ACTIVE)
				persistRecommendation(ownerId, sharedRecTeam)
				persistRecommendation(invitedUserId, sharedRecTeam)

				val teamId: Long = inviteTeam(ownerId, invitedUserId)
				acceptTeam(invitedUserId, teamId)

				val teamMatches: List<TeamMatchEntity> = allTeamMatches()
				teamMatches.size shouldBe 1
				teamMatches[0].memberKey shouldBe listOf(teamId, sharedRecTeam).sorted().joinToString("-")
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QMatchedTeamEntity.matchedTeamEntity)
		IntegrationUtil.deleteAll(QTeamMatchEntity.teamMatchEntity)
		IntegrationUtil.deleteAll(QRecommendedTeamEntity.recommendedTeamEntity)
		IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
		IntegrationUtil.deleteAll(QTeamEntity.teamEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
		IntegrationUtil.deleteAll(QAlarmEntity.alarmEntity)
	}
})

private fun allTeamMatches(): List<TeamMatchEntity> {
	val teamMatch: QTeamMatchEntity = QTeamMatchEntity.teamMatchEntity
	return IntegrationUtil.getQuery().selectFrom(teamMatch).fetch()
}

private fun matchedTeamsOf(teamMatchId: Long): List<MatchedTeamEntity> {
	val matched: QMatchedTeamEntity = QMatchedTeamEntity.matchedTeamEntity
	return IntegrationUtil.getQuery().selectFrom(matched).where(matched.teamMatchId.eq(teamMatchId)).fetch()
}
```

- [ ] **Step 3: 테스트 실패 확인 (서비스 미수정 시) / 통과 확인 (수정 후)**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.match.TeamMatchPromotionOnAcceptE2ETest"`
Expected: Step 1 수정이 적용된 상태면 PASS (4 tests). 만약 Step 1을 건너뛰면 `team_matches`가 비어 `size shouldBe 2`에서 FAIL.

- [ ] **Step 4: 전체 match E2E 회귀 확인**

기존 수락/초대 흐름이 깨지지 않았는지 확인:

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.match.AcceptTeamInvitationE2ETest" --tests "com.org.meeple.api.match.InviteTeamE2ETest" --tests "com.org.meeple.api.match.TeamMatchPromotionOnAcceptE2ETest"`
Expected: 모두 PASS

- [ ] **Step 5: 커밋**

```bash
git add meeple-core/src/main/kotlin/com/org/meeple/core/match/command/application/AcceptTeamInvitationService.kt \
        meeple-api/src/test/kotlin/com/org/meeple/api/match/TeamMatchPromotionOnAcceptE2ETest.kt
git commit -m "feat(match): 팀 결성 시 구성원 추천 팀을 TeamMatch로 승격

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review

**1. Spec coverage:**
- 두 멤버 모두 승격 → Task 3 `promoteRecommendedTeams` memberIds 순회 ✅
- 추천 없는 멤버 스킵 → `mapNotNull` ✅ (E2E 시나리오 2)
- 동일 추천 dedup → `distinct` ✅ (E2E 시나리오 4)
- 추천 팀 ACTIVE만 → `recommended.status == ACTIVE` ✅ (E2E 시나리오 3)
- recommended_teams 그대로 둠 → 어떤 태스크도 삭제/수정 안 함 ✅
- 초기 상태 PROPOSED/null → `TeamMatch.propose` 기본값 + Task1 단위테스트 + E2E 시나리오 1 ✅
- 새 matchType → Task 1 `RECOMMENDED` ✅
- 코인 40/40, 만료 1일 → Task 1 도메인 기본값 + 단위 테스트 ✅
- accept() 동기 처리 → Task 3 ✅
- 검증 방식(설계 ⚠️ 항목): testFixtures 헬퍼 대신 **E2E 인라인 QueryDSL 조회 함수**(`allTeamMatches`/`matchedTeamsOf`)로 검증 — 기존 E2E 컨벤션(`teamMembersOf`/`alarmsOf`)과 동일하며 리포지토리 직접 의존 아님. 설계의 미해결 항목을 이 방식으로 확정함.

**2. Placeholder scan:** 없음. 모든 코드 블록은 실제 구현/테스트 코드.

**3. Type consistency:**
- `findRecommendedTeamId(userId: Long): Long?` — Task 2 정의 / Task 3 사용 일치 ✅
- `save(teamMatch: TeamMatch): TeamMatch` — Task 2 정의 / Task 3 사용 일치 ✅
- `TeamMatch.propose(teamAId, teamBId, matchType, now)` — Task 1 정의 / Task 3 호출 시그니처 일치 ✅
- `MatchedTeams.of/teamIds/memberKey`, `MatchedTeam.copy(teamMatchId=...)` — Task 1 정의 / Task 2 사용 일치 ✅
- 엔티티 생성자 인자명(`TeamMatchEntity`, `MatchedTeamEntity`, `TeamEntity`) — 실제 정의와 일치 ✅
```
