# 솔로 유저 대상 팀 추천 (RecommendedTeam) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 팀이 없는 솔로 유저에게 반대 성별·같은 권역의 결성(ACTIVE) 팀 1개를 일일 배치로 추천 저장하고, 미팅탭에 표시한다.

**Architecture:** 헥사고날/CQRS. 추천 결과는 `recommended_teams` 포인터 행(`userId→teamId`)으로 저장하고, 표시 데이터는 읽기 시점에 `teams ⋈ team_members ⋈ match_user`를 조인해 채운다(staleness 자동 흡수). 쓰기(배치)는 `meeple-scheduler`(core 비의존)에 두고 out-port를 `meeple-infra` 어댑터가 구현한다. 읽기(표시)는 match `query` 슬라이스.

**Tech Stack:** Kotlin 2.2.21 / JVM 21, Spring Boot 4, Spring Data JPA + QueryDSL, MySQL, Kotest(유닛) + Testcontainers/RestAssured(E2E).

## Global Constraints

- 설계 문서: `docs/superpowers/specs/2026-06-22-recommended-team-design.md` (확정 요구사항의 출처).
- 추천 대상 팀: **반대 성별 + 결성(`TeamStatus.ACTIVE`) + 팀원 중 한 명이라도 요청자와 같은 `regionCode`**.
- 추천 수신 대상: `match_user`에 있으나 **비삭제 `team_members` 행이 전혀 없는**(=팀 미소속) 솔로 유저.
- 노출: 유저당 **1개**, **일일 배치** 생성, **주기마다 교체**(이력 누적 없음). `recommended_teams.user_id` 유니크로 upsert 교체.
- 타입 명시(변수·반환·람다 파라미터), `LocalDateTime.now()` 직접 호출 금지(`TimeGenerator` 주입), 명령/조회 분리, 엔티티당 어댑터 하나.
- 커밋 메시지: `<type>(match): <설명>`. 커밋 말미에 `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
- 빌드 검증: `./gradlew :meeple-api:test --tests "<클래스>"` (E2E는 meeple-api 모듈). QueryDSL Q클래스는 kapt가 생성하므로 새 엔티티 추가 후 `./gradlew :meeple-infra:kaptKotlin` 또는 전체 빌드로 `QRecommendedTeamEntity`를 생성해야 한다.

---

## File Structure

**Task 1 (읽기/표시 슬라이스 — 엔티티 포함)**
- Create `meeple-infra/.../match/command/entity/RecommendedTeamEntity.kt` — 추천 포인터 엔티티(`recommended_teams`).
- Create `meeple-core/.../match/query/dto/RecommendedTeam.kt` — 표시 read model(`RecommendedTeam` + `RecommendedTeamMember`).
- Create `meeple-core/.../match/query/dao/GetRecommendedTeamDao.kt` — 조회 dao 인터페이스.
- Create `meeple-infra/.../match/query/GetRecommendedTeamDaoImpl.kt` — QueryDSL 구현(조인).
- Create `meeple-core/.../match/query/service/port/in/GetRecommendedTeamUseCase.kt` — 인포트.
- Create `meeple-core/.../match/query/service/GetRecommendedTeamService.kt` — 조회 서비스.
- Create `meeple-api/.../match/response/RecommendedTeamResponse.kt` — 응답 DTO.
- Modify `meeple-api/.../match/TeamController.kt` — `GET /teams/v1/recommended-team` 추가.
- Create `meeple-infra/src/testFixtures/.../fixture/RecommendedTeamEntityFixture.kt` — 테스트 픽스처.
- Create `meeple-api/src/test/.../api/match/GetRecommendedTeamE2ETest.kt` — E2E.
- Doc `docs/migration/recommended_teams.sql` — DDL.

**Task 2 (쓰기/배치 슬라이스)**
- Create `meeple-infra/.../match/command/repository/RecommendedTeamJpaRepository.kt`
- Create `meeple-scheduler/.../match/command/application/port/out/SaveRecommendedTeamPort.kt`
- Create `meeple-infra/.../match/command/adapter/RecommendedTeamAdapter.kt` — `SaveRecommendedTeamPort` 구현(upsert).
- Create `meeple-scheduler/.../match/query/dto/RecommendableSoloUser.kt`
- Create `meeple-scheduler/.../match/query/dao/GetRecommendableSoloUserDao.kt`
- Create `meeple-infra/.../match/query/GetRecommendableSoloUserDaoImpl.kt` — 팀 미소속 솔로 유저(키셋 페이징).
- Create `meeple-scheduler/.../match/query/dao/GetCandidateTeamDao.kt`
- Create `meeple-infra/.../match/query/GetCandidateTeamDaoImpl.kt` — 후보 팀 1개(반대 성별·같은 권역·ACTIVE).
- Create `meeple-scheduler/.../match/command/domain/RecommendTeamBatchResult.kt`
- Create `meeple-scheduler/.../match/command/application/port/in/RunRecommendTeamBatchUseCase.kt`
- Create `meeple-scheduler/.../match/command/application/RunRecommendTeamBatchService.kt`
- Create `meeple-scheduler/.../match/command/application/RecommendTeamBatchJob.kt`
- Create `meeple-api/.../scheduler/match/RecommendTeamBatchScheduler.kt` — 크론 트리거.
- Create `meeple-api/.../admin/AdminRecommendTeamBatchController.kt` — 수동 트리거.
- Create `meeple-api/.../admin/response/RecommendTeamBatchResponse.kt`
- Modify `meeple-api/src/main/resources/application.yml` — 크론 프로퍼티.
- Create `meeple-api/src/test/.../api/admin/AdminRecommendTeamBatchE2ETest.kt` — E2E.

(경로 줄임: `meeple-infra/src/main/kotlin/com/org/meeple/infra`, `meeple-core/src/main/kotlin/com/org/meeple/core`, `meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler`, `meeple-api/src/main/kotlin/com/org/meeple`)

---

## Task 1: 읽기/표시 슬라이스 (엔티티 + 미팅탭 조회 엔드포인트)

**Files:**
- Create: `meeple-infra/src/main/kotlin/com/org/meeple/infra/match/command/entity/RecommendedTeamEntity.kt`
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/match/query/dto/RecommendedTeam.kt`
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/match/query/dao/GetRecommendedTeamDao.kt`
- Create: `meeple-infra/src/main/kotlin/com/org/meeple/infra/match/query/GetRecommendedTeamDaoImpl.kt`
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/match/query/service/port/in/GetRecommendedTeamUseCase.kt`
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/match/query/service/GetRecommendedTeamService.kt`
- Create: `meeple-api/src/main/kotlin/com/org/meeple/api/match/response/RecommendedTeamResponse.kt`
- Modify: `meeple-api/src/main/kotlin/com/org/meeple/api/match/TeamController.kt`
- Create: `meeple-infra/src/testFixtures/kotlin/com/org/meeple/infra/fixture/RecommendedTeamEntityFixture.kt`
- Create: `meeple-api/src/test/kotlin/com/org/meeple/api/match/GetRecommendedTeamE2ETest.kt`
- Doc: `docs/migration/recommended_teams.sql`

**Interfaces:**
- Produces:
  - `RecommendedTeamEntity(userId: Long, teamId: Long, recommendedDate: LocalDate) : BaseEntity` — `teamId`/`recommendedDate`는 `var`(upsert 갱신용), `userId`는 `val`(유니크 키). 테이블 `recommended_teams`, 유니크 `ux_user_id(user_id)`. (Task 2의 어댑터가 이 `var`로 교체한다)
  - `data class RecommendedTeam(teamId: Long, name: String, introduction: String?, members: List<RecommendedTeamMember>)`
  - `data class RecommendedTeamMember(userId: Long, nickname: String, gender: Gender, profileImageCode: String, birthday: LocalDate)`
  - `interface GetRecommendedTeamDao { fun findByUserId(userId: Long): RecommendedTeam? }`
  - `interface GetRecommendedTeamUseCase { fun get(userId: Long): RecommendedTeam? }`

- [ ] **Step 1: 실패 E2E 테스트 작성**

`meeple-api/src/test/kotlin/com/org/meeple/api/match/GetRecommendedTeamE2ETest.kt`:

```kotlin
package com.org.meeple.api.match

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.fixture.RecommendedTeamEntityFixture
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QRecommendedTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.infra.match.command.entity.TeamEntity
import com.org.meeple.infra.match.command.entity.TeamMemberEntity
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.nullValue
import java.time.LocalDate

/**
 * `GET /teams/v1/recommended-team` E2E 테스트. (팀 없는 솔로 유저의 미팅탭 추천 팀 표시)
 * 추천 행(recommended_teams)이 가리키는 ACTIVE 팀을 팀원 프로필과 함께 반환한다. 추천이 없거나 팀이 해체됐으면 data=null.
 */
class GetRecommendedTeamE2ETest : AbstractIntegrationSupport({

	// ACTIVE 팀 1개(여성 2명, 같은 권역)를 영속하고 teamId를 돌려준다. 팀원 match_user도 함께 적재한다.
	fun persistActiveFemaleTeam(member1: Long, member2: Long, regionCode: Int): Long {
		val team: TeamEntity = IntegrationUtil.persist(
			TeamEntity(name = "여성팀", introduction = "즐겁게 만나요", status = TeamStatus.ACTIVE),
		)
		val teamId: Long = team.id!!
		listOf(member1, member2).forEach { userId: Long ->
			IntegrationUtil.persist(
				TeamMemberEntity(teamId = teamId, userId = userId, gender = Gender.FEMALE, status = TeamMemberStatus.ACTIVE),
			)
			IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = Gender.FEMALE, regionCode = regionCode))
		}
		return teamId
	}

	describe("GET /teams/v1/recommended-team") {

		context("추천 행이 ACTIVE 팀을 가리키면") {
			it("그 팀과 팀원 프로필을 반환한다 (200)") {
				val soloUserId = 3001L
				IntegrationUtil.persist(MatchUserEntityFixture.create(userId = soloUserId, gender = Gender.MALE, regionCode = 1))
				val teamId: Long = persistActiveFemaleTeam(member1 = 3101L, member2 = 3102L, regionCode = 1)
				IntegrationUtil.persist(
					RecommendedTeamEntityFixture.create(userId = soloUserId, teamId = teamId, recommendedDate = LocalDate.of(2026, 6, 22)),
				)

				get("/teams/v1/recommended-team") {
					bearer(accessTokenFor(soloUserId))
				} expect {
					status(200)
					body("success", true)
					body("data.teamId", teamId.toInt())
					body("data.name", "여성팀")
					body("data.members", hasSize<Any>(2))
				}
			}
		}

		context("추천 행이 없으면") {
			it("data=null을 반환한다 (200)") {
				val soloUserId = 3002L
				IntegrationUtil.persist(MatchUserEntityFixture.create(userId = soloUserId, gender = Gender.MALE, regionCode = 1))

				get("/teams/v1/recommended-team") {
					bearer(accessTokenFor(soloUserId))
				} expect {
					status(200)
					body("data", nullValue())
				}
			}
		}

		context("추천 행이 가리키는 팀이 해체(soft delete)됐으면") {
			it("data=null을 반환한다 (200)") {
				val soloUserId = 3003L
				IntegrationUtil.persist(MatchUserEntityFixture.create(userId = soloUserId, gender = Gender.MALE, regionCode = 1))
				val team: TeamEntity = IntegrationUtil.persist(
					TeamEntity(name = "해체팀", introduction = "사라질 팀", status = TeamStatus.DEACTIVATED).also { it.softDelete(java.time.LocalDateTime.of(2026, 6, 21, 0, 0)) },
				)
				IntegrationUtil.persist(
					RecommendedTeamEntityFixture.create(userId = soloUserId, teamId = team.id!!, recommendedDate = LocalDate.of(2026, 6, 22)),
				)

				get("/teams/v1/recommended-team") {
					bearer(accessTokenFor(soloUserId))
				} expect {
					status(200)
					body("data", nullValue())
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QRecommendedTeamEntity.recommendedTeamEntity)
		IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
		IntegrationUtil.deleteAll(QTeamEntity.teamEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
	}
})
```

- [ ] **Step 2: 테스트 실행 → 컴파일 실패 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.match.GetRecommendedTeamE2ETest"`
Expected: FAIL — `RecommendedTeamEntity`/`QRecommendedTeamEntity`/`RecommendedTeamEntityFixture`/엔드포인트 미존재로 컴파일 에러.

- [ ] **Step 3: 엔티티 생성**

`meeple-infra/src/main/kotlin/com/org/meeple/infra/match/command/entity/RecommendedTeamEntity.kt`:

```kotlin
package com.org.meeple.infra.match.command.entity

import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDate

/**
 * 팀 없는 솔로 유저에게 추천된 결성(ACTIVE) 팀을 가리키는 포인터 엔티티. (표시 전용 — 솔로 유저는 신청 불가)
 * 일일 배치가 user_id 기준으로 교체(upsert)하므로 유저당 한 행만 유지한다. (soft delete·status·만료 없음)
 * 표시 데이터(팀명·팀원 프로필)는 조회 시점에 teams ⋈ team_members ⋈ match_user를 조인해 채운다.
 * (추천 팀이 해체되면 조회 조인의 teams(@SQLRestriction + status=ACTIVE)에서 빠져 자연히 노출되지 않는다)
 */
@Entity
@Table(
	name = "recommended_teams",
	uniqueConstraints = [
		// 솔로 유저당 추천 1개. 일일 배치가 이 키로 교체(upsert)한다.
		UniqueConstraint(name = "ux_user_id", columnNames = ["user_id"]),
	],
)
class RecommendedTeamEntity(
	/** 추천을 받는, 팀 없는 솔로 유저. (교체 키이므로 불변) */
	@Column(name = "user_id", nullable = false, updatable = false)
	val userId: Long,

	/** 추천된 ACTIVE(결성) 팀. (교체 시 갱신) */
	@Column(name = "team_id", nullable = false)
	var teamId: Long,

	/** 추천이 생성된 배치 일자. (관측·신선도 확인용, 교체 시 갱신) */
	@Column(name = "recommended_date", nullable = false)
	var recommendedDate: LocalDate,
) : BaseEntity()
```

- [ ] **Step 4: 읽기 모델·dao 인터페이스·인포트 생성**

`meeple-core/src/main/kotlin/com/org/meeple/core/match/query/dto/RecommendedTeam.kt`:

```kotlin
package com.org.meeple.core.match.query.dto

import com.org.meeple.common.user.Gender
import java.time.LocalDate

/**
 * 솔로 유저에게 추천된 팀 한 건(read model). 팀 메타와 그 팀의 ACTIVE 구성원 프로필을 담는다.
 * query 전용 view이며 command 도메인을 참조하지 않는다. (닉네임·프로필이미지·생일은 match_user, 성별은 team_members)
 */
data class RecommendedTeam(
	val teamId: Long,
	val name: String,
	val introduction: String?,
	val members: List<RecommendedTeamMember>,
)

/** 추천 팀 구성원 한 명의 표시 프로필. */
data class RecommendedTeamMember(
	val userId: Long,
	val nickname: String,
	val gender: Gender,
	val profileImageCode: String,
	val birthday: LocalDate,
)
```

`meeple-core/src/main/kotlin/com/org/meeple/core/match/query/dao/GetRecommendedTeamDao.kt`:

```kotlin
package com.org.meeple.core.match.query.dao

import com.org.meeple.core.match.query.dto.RecommendedTeam

/**
 * 솔로 유저에게 추천된 팀 조회 dao(query out-port). QueryDSL 구현은 infra가 담당한다.
 * 추천 행이 가리키는 ACTIVE 팀을 팀원 프로필과 함께 반환한다. 추천이 없거나 팀이 해체됐으면 null.
 */
interface GetRecommendedTeamDao {

	fun findByUserId(userId: Long): RecommendedTeam?
}
```

`meeple-core/src/main/kotlin/com/org/meeple/core/match/query/service/port/in/GetRecommendedTeamUseCase.kt`:

```kotlin
package com.org.meeple.core.match.query.service.port.`in`

import com.org.meeple.core.match.query.dto.RecommendedTeam

/**
 * 팀 없는 솔로 유저에게 추천된 팀을 조회하는 유스케이스(인포트). 추천이 없으면 null.
 */
interface GetRecommendedTeamUseCase {

	fun get(userId: Long): RecommendedTeam?
}
```

- [ ] **Step 5: dao 구현(QueryDSL 조인) 생성**

`meeple-infra/src/main/kotlin/com/org/meeple/infra/match/query/GetRecommendedTeamDaoImpl.kt`:

```kotlin
package com.org.meeple.infra.match.query

import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.core.match.query.dao.GetRecommendedTeamDao
import com.org.meeple.core.match.query.dto.RecommendedTeam
import com.org.meeple.core.match.query.dto.RecommendedTeamMember
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QRecommendedTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.querydsl.core.Tuple
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetRecommendedTeamDao]의 QueryDSL 구현체. (조회 전용)
 * ① 추천 행(recommended_teams) ⋈ teams(ACTIVE) 헤더를 단건 조회(유저당 추천 1개, teams @SQLRestriction이 해체 팀 제외),
 * ② 그 팀의 ACTIVE 구성원을 team_members ⋈ match_user로 프로필과 함께 조회해 [RecommendedTeam]으로 조립한다.
 * 추천이 없거나 팀이 해체(soft delete/비ACTIVE)됐으면 null.
 */
@Component
class GetRecommendedTeamDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetRecommendedTeamDao {

	override fun findByUserId(userId: Long): RecommendedTeam? {
		val recommended: QRecommendedTeamEntity = QRecommendedTeamEntity.recommendedTeamEntity
		val team: QTeamEntity = QTeamEntity.teamEntity

		val header: Tuple = queryFactory
			.select(team.id, team.name, team.introduction)
			.from(recommended)
			.join(team).on(team.id.eq(recommended.teamId))
			.where(
				recommended.userId.eq(userId),
				team.status.eq(TeamStatus.ACTIVE),
			)
			.fetchOne()
			?: return null

		val teamId: Long = header.get(team.id)!!

		val member: QTeamMemberEntity = QTeamMemberEntity.teamMemberEntity
		val matchUser: QMatchUserEntity = QMatchUserEntity.matchUserEntity
		val members: List<RecommendedTeamMember> = queryFactory
			.select(
				Projections.constructor(
					RecommendedTeamMember::class.java,
					member.userId,
					matchUser.nickname,
					member.gender,
					matchUser.profileImageCode,
					matchUser.birthday,
				),
			)
			.from(member)
			.join(matchUser).on(matchUser.userId.eq(member.userId))
			.where(
				member.teamId.eq(teamId),
				member.status.eq(TeamMemberStatus.ACTIVE),
			)
			.orderBy(member.userId.asc())
			.fetch()

		return RecommendedTeam(
			teamId = teamId,
			name = header.get(team.name)!!,
			introduction = header.get(team.introduction),
			members = members,
		)
	}
}
```

- [ ] **Step 6: 조회 서비스 생성**

`meeple-core/src/main/kotlin/com/org/meeple/core/match/query/service/GetRecommendedTeamService.kt`:

```kotlin
package com.org.meeple.core.match.query.service

import com.org.meeple.core.match.query.dao.GetRecommendedTeamDao
import com.org.meeple.core.match.query.dto.RecommendedTeam
import com.org.meeple.core.match.query.service.port.`in`.GetRecommendedTeamUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetRecommendedTeamUseCase] 구현. (조회 전용)
 * 팀 없는 솔로 유저에게 추천된 팀을 dao로 조회한다. (query dao만 의존, command 포트·도메인 미참조)
 */
@Service
@Transactional(readOnly = true)
class GetRecommendedTeamService(
	private val getRecommendedTeamDao: GetRecommendedTeamDao,
) : GetRecommendedTeamUseCase {

	override fun get(userId: Long): RecommendedTeam? =
		getRecommendedTeamDao.findByUserId(userId)
}
```

- [ ] **Step 7: 응답 DTO 생성**

`meeple-api/src/main/kotlin/com/org/meeple/api/match/response/RecommendedTeamResponse.kt`:

```kotlin
package com.org.meeple.api.match.response

import com.org.meeple.common.user.Gender
import com.org.meeple.core.common.time.ageAt
import com.org.meeple.core.match.query.dto.RecommendedTeam
import com.org.meeple.core.match.query.dto.RecommendedTeamMember
import java.time.LocalDate

/**
 * 미팅탭에 노출할 추천 팀 응답. 팀 메타와 팀원 표시 프로필(나이는 생일+오늘로 산출)을 담는다.
 */
data class RecommendedTeamResponse(
	val teamId: Long,
	val name: String,
	val introduction: String?,
	val members: List<Member>,
) {

	/** 추천 팀 구성원 항목. */
	data class Member(
		val userId: Long,
		val nickname: String,
		val gender: Gender,
		val profileImageCode: String,
		val age: Int,
	)

	companion object {
		/** 추천이 없으면(null) null을 그대로 돌려준다. (추천 없음 = data:null) */
		fun of(recommendedTeam: RecommendedTeam?, today: LocalDate): RecommendedTeamResponse? =
			recommendedTeam?.let {
				RecommendedTeamResponse(
					teamId = it.teamId,
					name = it.name,
					introduction = it.introduction,
					members = it.members.map { member: RecommendedTeamMember ->
						Member(
							userId = member.userId,
							nickname = member.nickname,
							gender = member.gender,
							profileImageCode = member.profileImageCode,
							age = member.birthday.ageAt(today),
						)
					},
				)
			}
	}
}
```

- [ ] **Step 8: TeamController에 엔드포인트 추가**

`meeple-api/.../match/TeamController.kt` 수정 — import에 추가:

```kotlin
import com.org.meeple.api.match.response.RecommendedTeamResponse
import com.org.meeple.core.match.query.service.port.`in`.GetRecommendedTeamUseCase
```

생성자에 의존성 추가(기존 `getReceivedInvitationsUseCase` 다음 줄):

```kotlin
	private val getRecommendedTeamUseCase: GetRecommendedTeamUseCase,
```

`searchInvitableUsers` 메서드 아래에 엔드포인트 추가:

```kotlin
	/**
	 * 팀이 없는 솔로 유저의 미팅탭에 노출할 추천 팀을 조회한다. (반대 성별·같은 권역의 결성(ACTIVE) 팀 1개)
	 * 일일 배치가 적재한 추천이 없거나 팀이 해체됐으면 data=null(200). (표시 전용 — 신청 불가)
	 */
	@Operation(summary = "추천 팀 조회", description = "팀이 없는 솔로 유저에게 추천된 결성(ACTIVE) 팀을 팀원 프로필과 함께 반환한다. 추천이 없으면 data=null(200).")
	@GetMapping("/recommended-team")
	fun getRecommendedTeam(
		@LoginUser user: AuthUser,
	): ApiResponse<RecommendedTeamResponse?> =
		ApiResponse.success(RecommendedTeamResponse.of(getRecommendedTeamUseCase.get(user.id), timeGenerator.today()))
```

- [ ] **Step 9: 테스트 픽스처 생성**

`meeple-infra/src/testFixtures/kotlin/com/org/meeple/infra/fixture/RecommendedTeamEntityFixture.kt`:

```kotlin
package com.org.meeple.infra.fixture

import com.org.meeple.infra.match.command.entity.RecommendedTeamEntity
import java.time.LocalDate

/**
 * [RecommendedTeamEntity] 테스트 픽스처. 추천 포인터(userId→teamId)와 추천 일자를 합리적 기본값으로 채운다.
 */
object RecommendedTeamEntityFixture {

	fun create(
		userId: Long = 1L,
		teamId: Long = 1L,
		recommendedDate: LocalDate = LocalDate.of(2026, 1, 1),
	): RecommendedTeamEntity =
		RecommendedTeamEntity(
			userId = userId,
			teamId = teamId,
			recommendedDate = recommendedDate,
		)
}
```

- [ ] **Step 10: 마이그레이션 DDL 문서 작성**

`docs/migration/recommended_teams.sql`:

```sql
-- 솔로 유저 대상 팀 추천(RecommendedTeam) 테이블. 유저당 1행(user_id 유니크)으로 일일 배치가 교체(upsert)한다.
CREATE TABLE recommended_teams (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    user_id          BIGINT       NOT NULL,
    team_id          BIGINT       NOT NULL,
    recommended_date DATE         NOT NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    deleted_at       DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT ux_user_id UNIQUE (user_id)
);
```

- [ ] **Step 11: 테스트 실행 → 통과 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.match.GetRecommendedTeamE2ETest"`
Expected: PASS (3개 컨텍스트 모두 통과).

- [ ] **Step 12: 커밋**

```bash
git add meeple-infra/src/main/kotlin/com/org/meeple/infra/match/command/entity/RecommendedTeamEntity.kt \
        meeple-core/src/main/kotlin/com/org/meeple/core/match/query/dto/RecommendedTeam.kt \
        meeple-core/src/main/kotlin/com/org/meeple/core/match/query/dao/GetRecommendedTeamDao.kt \
        meeple-infra/src/main/kotlin/com/org/meeple/infra/match/query/GetRecommendedTeamDaoImpl.kt \
        meeple-core/src/main/kotlin/com/org/meeple/core/match/query/service/port/in/GetRecommendedTeamUseCase.kt \
        meeple-core/src/main/kotlin/com/org/meeple/core/match/query/service/GetRecommendedTeamService.kt \
        meeple-api/src/main/kotlin/com/org/meeple/api/match/response/RecommendedTeamResponse.kt \
        meeple-api/src/main/kotlin/com/org/meeple/api/match/TeamController.kt \
        meeple-infra/src/testFixtures/kotlin/com/org/meeple/infra/fixture/RecommendedTeamEntityFixture.kt \
        meeple-api/src/test/kotlin/com/org/meeple/api/match/GetRecommendedTeamE2ETest.kt \
        docs/migration/recommended_teams.sql
git commit -m "$(cat <<'EOF'
feat(match): 솔로 유저 추천 팀(RecommendedTeam) 조회 슬라이스

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: 쓰기/배치 슬라이스 (일일 추천 배치 + 관리자 수동 트리거)

**Files:**
- Create: `meeple-infra/src/main/kotlin/com/org/meeple/infra/match/command/repository/RecommendedTeamJpaRepository.kt`
- Create: `meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/command/application/port/out/SaveRecommendedTeamPort.kt`
- Create: `meeple-infra/src/main/kotlin/com/org/meeple/infra/match/command/adapter/RecommendedTeamAdapter.kt`
- Create: `meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/query/dto/RecommendableSoloUser.kt`
- Create: `meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/query/dao/GetRecommendableSoloUserDao.kt`
- Create: `meeple-infra/src/main/kotlin/com/org/meeple/infra/match/query/GetRecommendableSoloUserDaoImpl.kt`
- Create: `meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/query/dao/GetCandidateTeamDao.kt`
- Create: `meeple-infra/src/main/kotlin/com/org/meeple/infra/match/query/GetCandidateTeamDaoImpl.kt`
- Create: `meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/command/domain/RecommendTeamBatchResult.kt`
- Create: `meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/command/application/port/in/RunRecommendTeamBatchUseCase.kt`
- Create: `meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/command/application/RunRecommendTeamBatchService.kt`
- Create: `meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/command/application/RecommendTeamBatchJob.kt`
- Create: `meeple-api/src/main/kotlin/com/org/meeple/scheduler/match/RecommendTeamBatchScheduler.kt`
- Create: `meeple-api/src/main/kotlin/com/org/meeple/api/admin/AdminRecommendTeamBatchController.kt`
- Create: `meeple-api/src/main/kotlin/com/org/meeple/api/admin/response/RecommendTeamBatchResponse.kt`
- Modify: `meeple-api/src/main/resources/application.yml`
- Create: `meeple-api/src/test/kotlin/com/org/meeple/api/admin/AdminRecommendTeamBatchE2ETest.kt`

**Interfaces:**
- Consumes (Task 1): `RecommendedTeamEntity(userId, teamId, recommendedDate)` (teamId/recommendedDate는 var).
- Produces:
  - `interface SaveRecommendedTeamPort { fun replace(userId: Long, teamId: Long, recommendedDate: LocalDate) }`
  - `data class RecommendableSoloUser(userId: Long, gender: Gender, regionCode: Int)`
  - `interface GetRecommendableSoloUserDao { fun findTargets(cursor: Long?, limit: Int): List<RecommendableSoloUser> }` (cursor=직전 페이지 마지막 userId, 첫 페이지 null)
  - `interface GetCandidateTeamDao { fun findOneCandidateTeamId(teamGender: Gender, regionCode: Int): Long? }` (teamGender=요청자 반대 성별)
  - `data class RecommendTeamBatchResult(targets: Int, recommended: Int, skipped: Int, failed: Int)`
  - `interface RunRecommendTeamBatchUseCase { fun run(): RecommendTeamBatchResult }`
  - `RecommendTeamBatchJob.run(): RecommendTeamBatchResult?` (이미 실행 중이면 null)

- [ ] **Step 1: 실패 E2E 테스트 작성**

`meeple-api/src/test/kotlin/com/org/meeple/api/admin/AdminRecommendTeamBatchE2ETest.kt`:

```kotlin
package com.org.meeple.api.admin

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.post
import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QRecommendedTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.infra.match.command.entity.RecommendedTeamEntity
import com.org.meeple.infra.match.command.entity.TeamEntity
import com.org.meeple.infra.match.command.entity.TeamMemberEntity
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.greaterThanOrEqualTo

/**
 * `POST /admin/v1/teams/recommend-batch` E2E 테스트. (관리자 전용 팀 추천 일일 배치 수동 실행)
 * 팀 없는 솔로 유저에게 반대 성별·같은 권역의 ACTIVE 팀 1개를 추천 적재한다. ROLE_ADMIN만 접근 가능.
 */
class AdminRecommendTeamBatchE2ETest : AbstractIntegrationSupport({

	// ACTIVE 팀(여성 2명, 같은 권역)을 영속하고 teamId를 돌려준다. (팀원 match_user도 적재)
	fun persistActiveFemaleTeam(member1: Long, member2: Long, regionCode: Int): Long {
		val team: TeamEntity = IntegrationUtil.persist(
			TeamEntity(name = "여성팀", introduction = "즐겁게 만나요", status = TeamStatus.ACTIVE),
		)
		val teamId: Long = team.id!!
		listOf(member1, member2).forEach { userId: Long ->
			IntegrationUtil.persist(
				TeamMemberEntity(teamId = teamId, userId = userId, gender = Gender.FEMALE, status = TeamMemberStatus.ACTIVE),
			)
			IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = Gender.FEMALE, regionCode = regionCode))
		}
		return teamId
	}

	fun recommendationOf(userId: Long): RecommendedTeamEntity? {
		val recommended: QRecommendedTeamEntity = QRecommendedTeamEntity.recommendedTeamEntity
		return IntegrationUtil.getQuery().selectFrom(recommended).where(recommended.userId.eq(userId)).fetchOne()
	}

	describe("POST /admin/v1/teams/recommend-batch") {

		context("팀 없는 솔로 유저와 반대 성별·같은 권역 ACTIVE 팀이 있으면") {
			it("그 유저에게 그 팀을 추천 적재한다 (200)") {
				val soloUserId = 4001L
				IntegrationUtil.persist(MatchUserEntityFixture.create(userId = soloUserId, gender = Gender.MALE, regionCode = 1))
				val teamId: Long = persistActiveFemaleTeam(member1 = 4101L, member2 = 4102L, regionCode = 1)

				post("/admin/v1/teams/recommend-batch") {
					bearer(adminAccessTokenFor(9101L))
				} expect {
					status(200)
					body("success", true)
					body("data.recommended", greaterThanOrEqualTo(1))
				}

				val recommendation: RecommendedTeamEntity? = recommendationOf(soloUserId)
				(recommendation != null) shouldBe true
				recommendation!!.teamId shouldBe teamId
			}
		}

		context("다시 실행하면") {
			it("유저당 추천 1행을 유지(교체)한다 (200)") {
				val soloUserId = 4002L
				IntegrationUtil.persist(MatchUserEntityFixture.create(userId = soloUserId, gender = Gender.MALE, regionCode = 1))
				persistActiveFemaleTeam(member1 = 4201L, member2 = 4202L, regionCode = 1)

				post("/admin/v1/teams/recommend-batch") { bearer(adminAccessTokenFor(9102L)) } expect { status(200) }
				post("/admin/v1/teams/recommend-batch") { bearer(adminAccessTokenFor(9102L)) } expect { status(200) }

				val recommended: QRecommendedTeamEntity = QRecommendedTeamEntity.recommendedTeamEntity
				val count: Long = IntegrationUtil.getQuery().select(recommended.count()).from(recommended)
					.where(recommended.userId.eq(soloUserId)).fetchOne() ?: 0L
				count shouldBe 1L
			}
		}

		context("일반 사용자(ROLE_USER)가 호출하면") {
			it("403을 반환한다") {
				post("/admin/v1/teams/recommend-batch") { bearer(accessTokenFor(9103L)) } expect { status(403) }
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				post("/admin/v1/teams/recommend-batch") {} expect { status(401) }
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QRecommendedTeamEntity.recommendedTeamEntity)
		IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
		IntegrationUtil.deleteAll(QTeamEntity.teamEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
	}
})
```

- [ ] **Step 2: 테스트 실행 → 컴파일 실패 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.admin.AdminRecommendTeamBatchE2ETest"`
Expected: FAIL — 엔드포인트/배치/포트/dao 미존재로 컴파일 에러.

- [ ] **Step 3: 리포지토리 + 저장 포트 + 어댑터(upsert) 생성**

`meeple-infra/.../match/command/repository/RecommendedTeamJpaRepository.kt`:

```kotlin
package com.org.meeple.infra.match.command.repository

import com.org.meeple.infra.match.command.entity.RecommendedTeamEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 추천 팀(recommended_teams) 리포지토리. PK는 생성 id지만 교체(upsert) 키는 user_id이므로 [findByUserId]로 단건 조회한다.
 */
interface RecommendedTeamJpaRepository : JpaRepository<RecommendedTeamEntity, Long> {

	/** 교체(upsert) 분기를 위해 user_id로 기존 추천 행을 조회한다. */
	fun findByUserId(userId: Long): RecommendedTeamEntity?
}
```

`meeple-scheduler/.../match/command/application/port/out/SaveRecommendedTeamPort.kt`:

```kotlin
package com.org.meeple.scheduler.match.command.application.port.out

import java.time.LocalDate

/**
 * 배치가 솔로 유저의 추천 팀을 적재(교체)하기 위한 아웃포트.
 * 추천 영속성은 infra가 갖고 있으므로 scheduler는 이 포트만 정의하고, 실제 upsert는 infra 어댑터가 구현한다.
 * (scheduler는 core에 의존하지 않는다)
 */
interface SaveRecommendedTeamPort {

	/** [userId]의 추천을 [teamId]로 교체(upsert)한다. 유저당 한 행만 유지한다. */
	fun replace(userId: Long, teamId: Long, recommendedDate: LocalDate)
}
```

`meeple-infra/.../match/command/adapter/RecommendedTeamAdapter.kt`:

```kotlin
package com.org.meeple.infra.match.command.adapter

import com.org.meeple.infra.match.command.entity.RecommendedTeamEntity
import com.org.meeple.infra.match.command.repository.RecommendedTeamJpaRepository
import com.org.meeple.scheduler.match.command.application.port.out.SaveRecommendedTeamPort
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * [RecommendedTeamEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나 — scheduler의 추천 적재 out-port를 구현)
 * user_id 기준 upsert: 기존 행이 있으면 team_id·추천 일자만 갱신(UPDATE), 없으면 새 행 INSERT. (유저당 1행 = 주기마다 교체)
 */
@Component
class RecommendedTeamAdapter(
	private val recommendedTeamJpaRepository: RecommendedTeamJpaRepository,
) : SaveRecommendedTeamPort {

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

- [ ] **Step 4: 대상(팀 미소속 솔로 유저) 조회 dao + dto 생성**

`meeple-scheduler/.../match/query/dto/RecommendableSoloUser.kt`:

```kotlin
package com.org.meeple.scheduler.match.query.dto

import com.org.meeple.common.user.Gender

/**
 * 팀 추천을 받을 솔로 유저 read model. (match_user에 있으나 어떤 팀에도 속하지 않은 유저)
 * 후보 팀 선정에 필요한 성별·권역만 담는다. (둘 다 match_user에서 non-null)
 */
data class RecommendableSoloUser(
	val userId: Long,
	val gender: Gender,
	val regionCode: Int,
)
```

`meeple-scheduler/.../match/query/dao/GetRecommendableSoloUserDao.kt`:

```kotlin
package com.org.meeple.scheduler.match.query.dao

import com.org.meeple.scheduler.match.query.dto.RecommendableSoloUser

/**
 * 팀 추천 대상(팀 미소속 솔로 유저) 조회 dao. QueryDSL 구현은 infra가 담당한다.
 * match_user에 있으나 비삭제 team_members 행이 전혀 없는 유저를 user_id 키셋으로 페이징해 반환한다.
 */
interface GetRecommendableSoloUserDao {

	/** [cursor](직전 페이지 마지막 user_id, 첫 페이지 null) 이후 user_id 오름차순으로 [limit]개를 반환한다. */
	fun findTargets(cursor: Long?, limit: Int): List<RecommendableSoloUser>
}
```

`meeple-infra/.../match/query/GetRecommendableSoloUserDaoImpl.kt`:

```kotlin
package com.org.meeple.infra.match.query

import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.scheduler.match.query.dao.GetRecommendableSoloUserDao
import com.org.meeple.scheduler.match.query.dto.RecommendableSoloUser
import com.querydsl.core.types.Projections
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * scheduler [GetRecommendableSoloUserDao]의 QueryDSL 구현. (조회 전용)
 * match_user 단독 베이스 + team_members NOT EXISTS로 팀 미소속 유저만 거른다.
 * (team_members @SQLRestriction이 소프트 삭제 행을 제외하므로, NOT EXISTS = 비삭제 소속이 전혀 없음 = 팀 미소속)
 * user_id 오름차순 키셋(ux_user_id) seek로 페이징한다. (filesort 없음)
 */
@Component
class GetRecommendableSoloUserDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetRecommendableSoloUserDao {

	override fun findTargets(cursor: Long?, limit: Int): List<RecommendableSoloUser> {
		val matchUser: QMatchUserEntity = QMatchUserEntity.matchUserEntity
		val teamMember: QTeamMemberEntity = QTeamMemberEntity.teamMemberEntity

		return queryFactory
			.select(
				Projections.constructor(
					RecommendableSoloUser::class.java,
					matchUser.userId,
					matchUser.gender,
					matchUser.regionCode,
				),
			)
			.from(matchUser)
			.where(
				cursor?.let { last: Long -> matchUser.userId.gt(last) },
				JPAExpressions.selectOne()
					.from(teamMember)
					.where(teamMember.userId.eq(matchUser.userId))
					.notExists(),
			)
			.orderBy(matchUser.userId.asc())
			.limit(limit.toLong())
			.fetch()
	}
}
```

- [ ] **Step 5: 후보 팀(반대 성별·같은 권역·ACTIVE) 조회 dao 생성**

`meeple-scheduler/.../match/query/dao/GetCandidateTeamDao.kt`:

```kotlin
package com.org.meeple.scheduler.match.query.dao

import com.org.meeple.common.user.Gender

/**
 * 추천 후보 팀 1개 조회 dao. QueryDSL 구현은 infra가 담당한다.
 * 결성(ACTIVE) + 팀원 성별이 [teamGender](요청자 반대 성별) + 팀원 중 한 명이라도 [regionCode]가 같은 팀 중 1개의 id를 반환한다.
 * 후보가 없으면 null.
 */
interface GetCandidateTeamDao {

	fun findOneCandidateTeamId(teamGender: Gender, regionCode: Int): Long?
}
```

`meeple-infra/.../match/query/GetCandidateTeamDaoImpl.kt`:

```kotlin
package com.org.meeple.infra.match.query

import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.scheduler.match.query.dao.GetCandidateTeamDao
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ThreadLocalRandom

/**
 * scheduler [GetCandidateTeamDao]의 QueryDSL 구현. (조회 전용)
 * teams 베이스에 두 EXISTS(① 팀 성별=teamGender, ② 팀원 중 한 명이라도 match_user.region_code=regionCode)로 후보를 좁힌다.
 * (EXISTS라 팀당 1행 → fan-out 없음) 후보 수를 센 뒤 [0,count) 랜덤 오프셋으로 1개의 team_id를 뽑는다.
 * (대규모에서 offset 스캔 비용이 커지면 커서/시드 기반으로 재검토한다 — 표시 전용 추천이라 현 단계에선 단순화)
 */
@Component
class GetCandidateTeamDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetCandidateTeamDao {

	override fun findOneCandidateTeamId(teamGender: Gender, regionCode: Int): Long? {
		val team: QTeamEntity = QTeamEntity.teamEntity

		val count: Long = queryFactory
			.select(team.count())
			.from(team)
			.where(*candidatePredicates(team, teamGender, regionCode))
			.fetchOne() ?: 0L
		if (count == 0L) return null

		val offset: Long = ThreadLocalRandom.current().nextLong(count)
		return queryFactory
			.select(team.id)
			.from(team)
			.where(*candidatePredicates(team, teamGender, regionCode))
			.orderBy(team.id.asc())
			.offset(offset)
			.limit(1)
			.fetchOne()
	}

	// 결성(ACTIVE) + 팀 성별=teamGender(팀은 동성 구성) + 팀원 중 한 명이라도 같은 권역.
	private fun candidatePredicates(team: QTeamEntity, teamGender: Gender, regionCode: Int): Array<BooleanExpression> {
		val genderMember: QTeamMemberEntity = QTeamMemberEntity("genderMember")
		val regionMember: QTeamMemberEntity = QTeamMemberEntity("regionMember")
		val regionMatchUser: QMatchUserEntity = QMatchUserEntity.matchUserEntity
		return arrayOf(
			team.status.eq(TeamStatus.ACTIVE),
			JPAExpressions.selectOne()
				.from(genderMember)
				.where(genderMember.teamId.eq(team.id), genderMember.gender.eq(teamGender))
				.exists(),
			JPAExpressions.selectOne()
				.from(regionMember)
				.join(regionMatchUser).on(regionMatchUser.userId.eq(regionMember.userId))
				.where(regionMember.teamId.eq(team.id), regionMatchUser.regionCode.eq(regionCode))
				.exists(),
		)
	}
}
```

- [ ] **Step 6: 배치 결과 VO + 인포트 생성**

`meeple-scheduler/.../match/command/domain/RecommendTeamBatchResult.kt`:

```kotlin
package com.org.meeple.scheduler.match.command.domain

/** 팀 추천 배치 실행 요약. */
data class RecommendTeamBatchResult(
	/** 순회한 대상(팀 미소속 솔로 유저) 수. */
	val targets: Int,
	/** 추천을 적재한 수. */
	val recommended: Int,
	/** 후보 팀이 없어 건너뛴 수. */
	val skipped: Int,
	/** 예기치 못한 오류로 실패한 수. */
	val failed: Int,
)
```

`meeple-scheduler/.../match/command/application/port/in/RunRecommendTeamBatchUseCase.kt`:

```kotlin
package com.org.meeple.scheduler.match.command.application.port.`in`

import com.org.meeple.scheduler.match.command.domain.RecommendTeamBatchResult

/**
 * 팀 추천 일일 배치 인포트(유스케이스).
 * 팀 미소속 솔로 유저를 순회하며 반대 성별·같은 권역의 ACTIVE 팀 1개를 추천 적재(교체)한다.
 * 개별 사용자 처리 실패가 전체 배치를 멈추지 않는다.
 */
interface RunRecommendTeamBatchUseCase {

	fun run(): RecommendTeamBatchResult
}
```

- [ ] **Step 7: 배치 서비스 생성**

`meeple-scheduler/.../match/command/application/RunRecommendTeamBatchService.kt`:

```kotlin
package com.org.meeple.scheduler.match.command.application

import com.org.meeple.common.user.Gender
import com.org.meeple.scheduler.match.command.application.port.`in`.RunRecommendTeamBatchUseCase
import com.org.meeple.scheduler.match.command.application.port.out.SaveRecommendedTeamPort
import com.org.meeple.scheduler.match.command.application.port.out.TimeGenerator
import com.org.meeple.scheduler.match.command.domain.RecommendTeamBatchResult
import com.org.meeple.scheduler.match.query.dao.GetCandidateTeamDao
import com.org.meeple.scheduler.match.query.dao.GetRecommendableSoloUserDao
import com.org.meeple.scheduler.match.query.dto.RecommendableSoloUser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * [RunRecommendTeamBatchUseCase] 구현.
 * 팀 미소속 솔로 유저를 user_id 키셋 페이징으로 순회하며, 각자에게 반대 성별·같은 권역의 ACTIVE 팀 1개를 추천 적재(교체)한다.
 * 후보 팀이 없으면 건너뛴다. 전체를 한 트랜잭션으로 묶지 않으며, 한 사용자의 실패가 다른 사용자에게 전파되지 않게 사용자 단위로 격리한다.
 */
@Service
class RunRecommendTeamBatchService(
	private val getRecommendableSoloUserDao: GetRecommendableSoloUserDao,
	private val getCandidateTeamDao: GetCandidateTeamDao,
	private val saveRecommendedTeamPort: SaveRecommendedTeamPort,
	private val timeGenerator: TimeGenerator,
) : RunRecommendTeamBatchUseCase {

	private val log: Logger = LoggerFactory.getLogger(javaClass)

	override fun run(): RecommendTeamBatchResult {
		val today: LocalDate = timeGenerator.today()

		var cursor: Long? = null
		var targets = 0
		var recommended = 0
		var skipped = 0
		var failed = 0

		while (true) {
			val page: List<RecommendableSoloUser> = getRecommendableSoloUserDao.findTargets(cursor, PAGE_SIZE)
			if (page.isEmpty()) break

			for (target: RecommendableSoloUser in page) {
				targets++
				try {
					// 팀은 동성 구성이므로, 요청자의 반대 성별 = 추천 팀의 성별.
					val teamGender: Gender = target.gender.opposite()
					val teamId: Long? = getCandidateTeamDao.findOneCandidateTeamId(teamGender, target.regionCode)
					if (teamId != null) {
						saveRecommendedTeamPort.replace(target.userId, teamId, today)
						recommended++
					} else {
						skipped++
					}
				} catch (e: Exception) {
					failed++
					log.warn("팀 추천 배치 처리 실패 userId={}", target.userId, e)
				}
			}

			cursor = page.last().userId
		}

		val result = RecommendTeamBatchResult(targets = targets, recommended = recommended, skipped = skipped, failed = failed)
		log.info("팀 추천 배치 완료: {}", result)
		return result
	}

	companion object {
		/** 한 번에 조회·순회하는 대상 페이지 크기. */
		private const val PAGE_SIZE = 500
	}
}
```

- [ ] **Step 8: 배치 진입점(Job) 생성**

`meeple-scheduler/.../match/command/application/RecommendTeamBatchJob.kt`:

```kotlin
package com.org.meeple.scheduler.match.command.application

import com.org.meeple.scheduler.match.command.application.port.`in`.RunRecommendTeamBatchUseCase
import com.org.meeple.scheduler.match.command.domain.RecommendTeamBatchResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 팀 추천 일일 배치 실행 진입점. (크론 / 관리자 수동 트리거 공통)
 * [RunRecommendTeamBatchUseCase]를 실행하고 시작/종료를 로깅한다. 프로세스 내 가드([running])로 동시/중복 실행을 막는다.
 */
@Component
class RecommendTeamBatchJob(
	private val runRecommendTeamBatchUseCase: RunRecommendTeamBatchUseCase,
) {

	private val log: Logger = LoggerFactory.getLogger(javaClass)

	/** 현재 배치 실행 중 여부. (크론·수동 트리거 동시 실행 방지) */
	private val running: AtomicBoolean = AtomicBoolean(false)

	/** 배치를 실행하고 결과를 반환한다. 이미 실행 중이면 null. */
	fun run(): RecommendTeamBatchResult? {
		if (!running.compareAndSet(false, true)) {
			log.warn("팀 추천 배치가 이미 실행 중이라 이번 트리거는 건너뜁니다.")
			return null
		}
		return try {
			log.info("팀 추천 배치 시작")
			val result: RecommendTeamBatchResult = runRecommendTeamBatchUseCase.run()
			log.info("팀 추천 배치 종료: {}", result)
			result
		} finally {
			running.set(false)
		}
	}
}
```

- [ ] **Step 9: 크론 트리거 + 프로퍼티 추가**

`meeple-api/.../scheduler/match/RecommendTeamBatchScheduler.kt`:

```kotlin
package com.org.meeple.scheduler.match

import com.org.meeple.scheduler.match.command.application.RecommendTeamBatchJob
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 팀 추천 일일 배치 스케줄러(크론 트리거).
 * 정해진 크론 시각마다 [RecommendTeamBatchJob]을 실행한다. 실행 주기는 meeple.match.recommend-team-batch.cron 프로퍼티로 조정한다.
 */
@Component
class RecommendTeamBatchScheduler(
	private val recommendTeamBatchJob: RecommendTeamBatchJob,
) {

	@Scheduled(cron = "\${meeple.match.recommend-team-batch.cron}", zone = "Asia/Seoul")
	fun runRecommendTeam() {
		recommendTeamBatchJob.run()
	}
}
```

`meeple-api/src/main/resources/application.yml` 수정 — `meeple.match` 아래 `expire` 블록 다음에 추가:

```yaml
    recommend-team-batch:
      # 팀 없는 솔로 유저에게 결성 팀 추천. 매일 04:00 (Asia/Seoul). 운영에서 MEEPLE_RECOMMEND_TEAM_BATCH_CRON 환경변수로 덮어쓸 수 있다.
      cron: ${MEEPLE_RECOMMEND_TEAM_BATCH_CRON:0 0 4 * * *}
```

- [ ] **Step 10: 관리자 수동 트리거 컨트롤러 + 응답 생성**

`meeple-api/.../admin/response/RecommendTeamBatchResponse.kt`:

```kotlin
package com.org.meeple.api.admin.response

import com.org.meeple.scheduler.match.command.domain.RecommendTeamBatchResult

/**
 * 팀 추천 배치 실행 결과 응답. (관리자 수동 트리거)
 */
data class RecommendTeamBatchResponse(
	/** 순회한 대상(팀 미소속 솔로 유저) 수. */
	val targets: Int,
	/** 추천을 적재한 수. */
	val recommended: Int,
	/** 후보 팀이 없어 건너뛴 수. */
	val skipped: Int,
	/** 예기치 못한 오류로 실패한 수. */
	val failed: Int,
) {
	companion object {
		fun of(result: RecommendTeamBatchResult): RecommendTeamBatchResponse =
			RecommendTeamBatchResponse(
				targets = result.targets,
				recommended = result.recommended,
				skipped = result.skipped,
				failed = result.failed,
			)
	}
}
```

`meeple-api/.../admin/AdminRecommendTeamBatchController.kt`:

```kotlin
package com.org.meeple.api.admin

import com.org.meeple.api.admin.response.RecommendTeamBatchResponse
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.match.MatchErrorCode
import com.org.meeple.scheduler.match.command.application.RecommendTeamBatchJob
import com.org.meeple.scheduler.match.command.domain.RecommendTeamBatchResult
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 관리자 전용 팀 추천 배치 엔드포인트. (admin 경로는 SecurityConfig에서 ROLE_ADMIN으로 제한)
 * 크론과 동일한 진입점([RecommendTeamBatchJob])을 호출해 팀 추천 배치를 즉시(동기) 실행한다.
 */
@Tag(name = "관리자 - 팀 추천 배치", description = "관리자 전용 팀 추천 배치 엔드포인트. 크론과 동일한 진입점을 호출해 팀 추천 배치를 즉시 실행한다.")
@RestController
@RequestMapping("/admin/v1")
class AdminRecommendTeamBatchController(
	private val recommendTeamBatchJob: RecommendTeamBatchJob,
) {

	/**
	 * 팀 추천 배치를 즉시 실행하고 결과를 반환한다.
	 * 이미 실행 중이면 [MatchErrorCode.MATCH_BATCH_ALREADY_RUNNING](409)을 던진다. (배치 진행 중 의미를 공유)
	 */
	@Operation(summary = "팀 추천 배치 즉시 실행", description = "팀 추천 배치를 즉시(동기) 실행하고 결과를 반환한다. 이미 실행 중이면 409를 반환한다.")
	@PostMapping("/teams/recommend-batch")
	fun runRecommendTeamBatch(): ApiResponse<RecommendTeamBatchResponse> {
		val result: RecommendTeamBatchResult = recommendTeamBatchJob.run()
			?: throw BusinessException(MatchErrorCode.MATCH_BATCH_ALREADY_RUNNING)
		return ApiResponse.success(RecommendTeamBatchResponse.of(result))
	}
}
```

> 확인: `MatchErrorCode.MATCH_BATCH_ALREADY_RUNNING`이 존재하는지 본다(`AdminMatchBatchController`가 이미 사용). 없거나 이름이 다르면 그 코드명에 맞춘다.

- [ ] **Step 11: 테스트 실행 → 통과 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.admin.AdminRecommendTeamBatchE2ETest"`
Expected: PASS (추천 적재·교체·403·401 모두 통과).

- [ ] **Step 12: Task 1 E2E 회귀 확인 + 커밋**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.match.GetRecommendedTeamE2ETest" --tests "com.org.meeple.api.admin.AdminRecommendTeamBatchE2ETest"`
Expected: PASS (양쪽 모두).

```bash
git add meeple-infra/src/main/kotlin/com/org/meeple/infra/match/command/repository/RecommendedTeamJpaRepository.kt \
        meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/command/application/port/out/SaveRecommendedTeamPort.kt \
        meeple-infra/src/main/kotlin/com/org/meeple/infra/match/command/adapter/RecommendedTeamAdapter.kt \
        meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/query/dto/RecommendableSoloUser.kt \
        meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/query/dao/GetRecommendableSoloUserDao.kt \
        meeple-infra/src/main/kotlin/com/org/meeple/infra/match/query/GetRecommendableSoloUserDaoImpl.kt \
        meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/query/dao/GetCandidateTeamDao.kt \
        meeple-infra/src/main/kotlin/com/org/meeple/infra/match/query/GetCandidateTeamDaoImpl.kt \
        meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/command/domain/RecommendTeamBatchResult.kt \
        meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/command/application/port/in/RunRecommendTeamBatchUseCase.kt \
        meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/command/application/RunRecommendTeamBatchService.kt \
        meeple-scheduler/src/main/kotlin/com/org/meeple/scheduler/match/command/application/RecommendTeamBatchJob.kt \
        meeple-api/src/main/kotlin/com/org/meeple/scheduler/match/RecommendTeamBatchScheduler.kt \
        meeple-api/src/main/kotlin/com/org/meeple/api/admin/AdminRecommendTeamBatchController.kt \
        meeple-api/src/main/kotlin/com/org/meeple/api/admin/response/RecommendTeamBatchResponse.kt \
        meeple-api/src/main/resources/application.yml \
        meeple-api/src/test/kotlin/com/org/meeple/api/admin/AdminRecommendTeamBatchE2ETest.kt
git commit -m "$(cat <<'EOF'
feat(match): 솔로 유저 팀 추천 일일 배치 + 관리자 수동 트리거

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## 실행 순서 / 검증 요약

1. Task 1 → `GetRecommendedTeamE2ETest` 통과 (엔티티 + 표시 슬라이스).
2. Task 2 → `AdminRecommendTeamBatchE2ETest` 통과 (배치 적재·교체) + Task 1 회귀.
3. 전체 빌드: `./gradlew build` 로 컴파일·전체 테스트 확인.

## 알려진 단순화 (설계 §4.3 / 범위 외)

- 후보 팀 선정은 대상 1명당 count+offset 2쿼리다. 솔로 일일 배치의 Redis 풀 방식과 달리 사전 적재 없이 per-user 조회한다(표시 전용·저빈도 가정). 대규모로 커지면 권역별 후보 팀을 사전 적재하는 방식으로 재검토한다.
- `recommended_teams`는 soft delete를 쓰지 않는다(주기마다 하드 교체). 추천 팀이 장중 해체돼도 표시 조회 조인이 흡수하므로 즉시 정리하지 않는다.
- 관리자 트리거의 "이미 실행 중" 응답은 `MatchErrorCode.MATCH_BATCH_ALREADY_RUNNING`을 재사용한다(배치 진행 중 의미 공유). 별도 에러 코드는 두지 않는다.
