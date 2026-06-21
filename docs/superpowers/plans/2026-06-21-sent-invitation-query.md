# 내가 보낸 초대 현황 조회 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 초대자(owner)가 자신이 보낸 가장 최근 INVITING 초대의 현황을 조회하는 `GET /teams/v1/invitation` 엔드포인트를 추가한다.

**Architecture:** 헥사고날 CQRS query 슬라이스로 구현한다. core query에 read model(DTO)·in-port(UseCase)·out-port(Dao)·service를 두고, infra query에 QueryDSL daoImpl을, api에 응답 DTO·컨트롤러 메서드를 둔다. query는 자기 dao에만 의존하고 command 도메인·포트를 참조하지 않는다. 기존 `SearchInvitableUsers` 슬라이스 구조를 그대로 따른다.

**Tech Stack:** Kotlin 2.2.21 / Spring Boot 4 / Spring Data JPA / QueryDSL / Kotest(E2E) + Testcontainers.

## Global Constraints

- 타입 명시: 변수·반환 타입·람다 파라미터 타입을 생략하지 않는다.
- query는 command 도메인·포트를 참조하지 않고 자기 dao에만 의존한다.
- 조회 서비스는 `@Transactional(readOnly = true)`.
- 컨트롤러는 Service가 아니라 in-port `UseCase`를 주입한다.
- 진행 중인 초대가 없으면 `data=null`(HTTP 200) — 404 아님.
- `GET /teams/v1/invitation` 경로 고정.
- 인덱스 신규 추가 없음(기존 `idx_user_id`, `ux_team_id_user_id`로 seek).

---

## File Structure

| 파일 | 책임 | Task |
|---|---|---|
| `meeple-core/.../match/query/dto/SentInvitation.kt` | read model (`SentInvitation`, `SentInvitationMember`) | 1 |
| `meeple-core/.../match/query/service/port/in/GetSentInvitationUseCase.kt` | in-port | 1 |
| `meeple-core/.../match/query/dao/GetSentInvitationDao.kt` | query out-port | 1 |
| `meeple-core/.../match/query/service/GetSentInvitationService.kt` | UseCase 구현 | 1 |
| `meeple-infra/.../match/query/GetSentInvitationDaoImpl.kt` | QueryDSL 구현 | 1 |
| `meeple-api/.../match/response/SentInvitationResponse.kt` | 응답 DTO | 2 |
| `meeple-api/.../match/TeamController.kt` | `getSentInvitation()` 메서드 추가 | 2 |
| `meeple-api/.../test/.../match/GetSentInvitationE2ETest.kt` | E2E 테스트 | 2 |

---

## Task 1: core query 슬라이스 + infra QueryDSL dao

요청자가 ACTIVE 구성원(=초대자)인 가장 최근 INVITING 팀을 구성원과 함께 조회하는 query 경로를 구현한다. (HTTP 경계는 Task 2)

**Files:**
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/match/query/dto/SentInvitation.kt`
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/match/query/service/port/in/GetSentInvitationUseCase.kt`
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/match/query/dao/GetSentInvitationDao.kt`
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/match/query/service/GetSentInvitationService.kt`
- Create: `meeple-infra/src/main/kotlin/com/org/meeple/infra/match/query/GetSentInvitationDaoImpl.kt`

**Interfaces:**
- Produces (Task 2가 사용):
  - `SentInvitation(teamId: Long, name: String, introduction: String?, status: TeamStatus, members: List<SentInvitationMember>)`
  - `SentInvitationMember(userId: Long, status: TeamMemberStatus)`
  - `GetSentInvitationUseCase.get(requesterId: Long): SentInvitation?`

- [ ] **Step 1: read model DTO 작성**

`meeple-core/src/main/kotlin/com/org/meeple/core/match/query/dto/SentInvitation.kt`:

```kotlin
package com.org.meeple.core.match.query.dto

import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus

/**
 * 내가 보낸 초대 현황(read model). 초대자(owner)가 자신이 보낸 초대 팀의 메타와 구성원 현황을 본다.
 * query 전용 view이며 command 도메인을 참조하지 않는다.
 */
data class SentInvitation(
	val teamId: Long,
	val name: String,
	val introduction: String?,
	val status: TeamStatus,
	val members: List<SentInvitationMember>,
)

/** 초대 팀 구성원 한 명의 현황. status=ACTIVE는 초대자, INVITED는 수락 대기 중인 초대 대상. */
data class SentInvitationMember(
	val userId: Long,
	val status: TeamMemberStatus,
)
```

- [ ] **Step 2: in-port(UseCase) 작성**

`meeple-core/src/main/kotlin/com/org/meeple/core/match/query/service/port/in/GetSentInvitationUseCase.kt`:

```kotlin
package com.org.meeple.core.match.query.service.port.`in`

import com.org.meeple.core.match.query.dto.SentInvitation

/**
 * 내가 보낸 초대 현황을 조회하는 유스케이스(인포트).
 * 요청자가 ACTIVE 구성원(=초대자)인 INVITING 팀 중 가장 최근 1건을 반환한다. 없으면 null.
 */
interface GetSentInvitationUseCase {

	/** [requesterId]가 보낸 가장 최근 INVITING 초대 현황을 조회한다. 없으면 null. */
	fun get(requesterId: Long): SentInvitation?
}
```

- [ ] **Step 3: out-port(Dao) 작성**

`meeple-core/src/main/kotlin/com/org/meeple/core/match/query/dao/GetSentInvitationDao.kt`:

```kotlin
package com.org.meeple.core.match.query.dao

import com.org.meeple.core.match.query.dto.SentInvitation

/**
 * 내가 보낸 초대 현황 조회 dao(query out-port). 실제 QueryDSL 구현은 infra가 담당한다.
 * 요청자가 ACTIVE 구성원(=초대자)인 INVITING 팀 중 가장 최근 1건을 구성원과 함께 조회한다.
 */
interface GetSentInvitationDao {

	/** [requesterId]가 ACTIVE 구성원인 가장 최근 INVITING 팀을 구성원과 함께 조회한다. 없으면 null. */
	fun findLatestInviting(requesterId: Long): SentInvitation?
}
```

- [ ] **Step 4: query service 작성**

`meeple-core/src/main/kotlin/com/org/meeple/core/match/query/service/GetSentInvitationService.kt`:

```kotlin
package com.org.meeple.core.match.query.service

import com.org.meeple.core.match.query.dao.GetSentInvitationDao
import com.org.meeple.core.match.query.dto.SentInvitation
import com.org.meeple.core.match.query.service.port.`in`.GetSentInvitationUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetSentInvitationUseCase] 구현. (조회 전용)
 * 요청자가 ACTIVE 구성원(=초대자)인 가장 최근 INVITING 팀을 dao로 조회한다. 없으면 null.
 * (query dao만 의존하고 command 포트·도메인을 참조하지 않는다)
 */
@Service
@Transactional(readOnly = true)
class GetSentInvitationService(
	private val getSentInvitationDao: GetSentInvitationDao,
) : GetSentInvitationUseCase {

	override fun get(requesterId: Long): SentInvitation? =
		getSentInvitationDao.findLatestInviting(requesterId)
}
```

- [ ] **Step 5: infra QueryDSL daoImpl 작성**

`meeple-infra/src/main/kotlin/com/org/meeple/infra/match/query/GetSentInvitationDaoImpl.kt`:

```kotlin
package com.org.meeple.infra.match.query

import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.core.match.query.dao.GetSentInvitationDao
import com.org.meeple.core.match.query.dto.SentInvitation
import com.org.meeple.core.match.query.dto.SentInvitationMember
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.querydsl.core.Tuple
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetSentInvitationDao]의 QueryDSL 구현체. (조회 전용)
 * ① 요청자가 ACTIVE 구성원(=초대자)인 INVITING 팀 헤더를 team.id desc로 1건 조회(team_members idx_user_id seek → status 필터 + teams PK 조인),
 * ② 그 팀의 구성원(userId·status)을 조회해 [SentInvitation]으로 조립한다.
 * [org.hibernate.annotations.SQLRestriction]이 소프트 삭제(철회·해체)된 행을 자동 제외한다. (신규 인덱스 불필요)
 */
@Component
class GetSentInvitationDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetSentInvitationDao {

	override fun findLatestInviting(requesterId: Long): SentInvitation? {
		val team: QTeamEntity = QTeamEntity.teamEntity
		val teamMember: QTeamMemberEntity = QTeamMemberEntity.teamMemberEntity

		val header: Tuple = queryFactory
			.select(team.id, team.name, team.introduction, team.status)
			.from(teamMember)
			.join(team).on(team.id.eq(teamMember.teamId))
			.where(
				teamMember.userId.eq(requesterId),
				teamMember.status.eq(TeamMemberStatus.ACTIVE),
				team.status.eq(TeamStatus.INVITING),
			)
			.orderBy(team.id.desc())
			.limit(1)
			.fetchOne()
			?: return null

		val teamId: Long = header.get(team.id)!!

		val members: List<SentInvitationMember> = queryFactory
			.select(Projections.constructor(SentInvitationMember::class.java, teamMember.userId, teamMember.status))
			.from(teamMember)
			.where(teamMember.teamId.eq(teamId))
			.orderBy(teamMember.userId.asc())
			.fetch()

		return SentInvitation(
			teamId = teamId,
			name = header.get(team.name)!!,
			introduction = header.get(team.introduction),
			status = header.get(team.status)!!,
			members = members,
		)
	}
}
```

- [ ] **Step 6: 컴파일 검증**

Run: `./gradlew :meeple-infra:compileKotlin`
Expected: BUILD SUCCESSFUL (meeple-infra는 core에 의존하므로 core query 슬라이스도 함께 컴파일된다)

- [ ] **Step 7: Commit**

```bash
git add meeple-core/src/main/kotlin/com/org/meeple/core/match/query \
        meeple-infra/src/main/kotlin/com/org/meeple/infra/match/query/GetSentInvitationDaoImpl.kt
git commit -m "feat: 내가 보낸 초대 현황 조회 query 슬라이스(core·infra)"
```

---

## Task 2: api 엔드포인트 + E2E 테스트

`GET /teams/v1/invitation`을 추가하고 E2E로 동작을 검증한다.

**Files:**
- Create: `meeple-api/src/main/kotlin/com/org/meeple/api/match/response/SentInvitationResponse.kt`
- Modify: `meeple-api/src/main/kotlin/com/org/meeple/api/match/TeamController.kt`
- Create (test): `meeple-api/src/test/kotlin/com/org/meeple/api/match/GetSentInvitationE2ETest.kt`

**Interfaces:**
- Consumes (Task 1에서):
  - `GetSentInvitationUseCase.get(requesterId: Long): SentInvitation?`
  - `SentInvitation`, `SentInvitationMember`

- [ ] **Step 1: 응답 DTO 작성**

`meeple-api/src/main/kotlin/com/org/meeple/api/match/response/SentInvitationResponse.kt`:

```kotlin
package com.org.meeple.api.match.response

import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.core.match.query.dto.SentInvitation

/**
 * 내가 보낸 초대 현황 응답. 팀 식별자·이름·소개·상태와 구성원(userId·수락 상태)을 담는다.
 */
data class SentInvitationResponse(
	val teamId: Long,
	val name: String,
	val introduction: String?,
	val status: TeamStatus,
	val members: List<Member>,
) {

	/** 구성원 현황 항목. status=ACTIVE는 초대자, INVITED는 수락 대기 중인 초대 대상. */
	data class Member(
		val userId: Long,
		val status: TeamMemberStatus,
	)

	companion object {
		fun of(invitation: SentInvitation): SentInvitationResponse =
			SentInvitationResponse(
				teamId = invitation.teamId,
				name = invitation.name,
				introduction = invitation.introduction,
				status = invitation.status,
				members = invitation.members.map { member: SentInvitationMember -> Member(userId = member.userId, status = member.status) },
			)
	}
}
```

(상단 import에 `import com.org.meeple.core.match.query.dto.SentInvitationMember` 추가)

- [ ] **Step 2: 컨트롤러에 엔드포인트 추가**

`TeamController.kt` 수정:

1. import 추가:
```kotlin
import com.org.meeple.api.match.response.SentInvitationResponse
import com.org.meeple.core.match.query.service.port.`in`.GetSentInvitationUseCase
```

2. 생성자에 의존성 추가 (`searchInvitableUsersUseCase` 다음 줄):
```kotlin
	private val getSentInvitationUseCase: GetSentInvitationUseCase,
```

3. 메서드 추가 (`searchInvitableUsers` 메서드 위, 명령 그룹과 조회 그룹 사이 또는 클래스 끝):
```kotlin
	/**
	 * 내가 보낸 초대 현황을 조회한다. 요청자가 ACTIVE 구성원(=초대자)인 가장 최근 INVITING 팀을 반환한다.
	 * 진행 중인 초대가 없으면 data=null(200). (초대받은 사람·비구성원은 조회되지 않아 초대자에게만 노출된다)
	 */
	@GetMapping("/invitation")
	fun getSentInvitation(
		@LoginUser user: AuthUser,
	): ApiResponse<SentInvitationResponse?> =
		ApiResponse.success(getSentInvitationUseCase.get(user.id)?.let { invitation: SentInvitation -> SentInvitationResponse.of(invitation) })
```

4. `SentInvitation` 타입 사용을 위해 import 추가:
```kotlin
import com.org.meeple.core.match.query.dto.SentInvitation
```

- [ ] **Step 3: E2E 테스트 작성**

`meeple-api/src/test/kotlin/com/org/meeple/api/match/GetSentInvitationE2ETest.kt`:

```kotlin
package com.org.meeple.api.match

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.delete
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.integration.post
import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.nullValue

/**
 * `GET /teams/v1/invitation` E2E 테스트. (내가 보낸 초대 현황 조회)
 * 요청자가 ACTIVE 구성원(=초대자)인 가장 최근 INVITING 팀을 반환한다.
 * 초대받은 사람·비구성원·철회된 경우는 data=null(200)로 노출되지 않는다.
 */
class GetSentInvitationE2ETest : AbstractIntegrationSupport({

	fun persistMatchUser(userId: Long, gender: Gender) {
		IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = gender))
	}

	// 팀을 결성(초대)하고 teamId를 돌려준다.
	fun inviteTeam(ownerId: Long, invitedUserId: Long): Long {
		persistMatchUser(ownerId, Gender.MALE)
		persistMatchUser(invitedUserId, Gender.MALE)
		return post("/teams/v1/invitation") {
			bearer(accessTokenFor(ownerId))
			jsonBody("""{"invitedUserId": $invitedUserId, "name": "우리팀", "introduction": "함께 즐겁게 활동할 팀이에요"}""")
		}.extract().path<Int>("data.teamId").toLong()
	}

	describe("GET /teams/v1/invitation") {

		context("초대자가 자신이 보낸 초대를 조회하면") {
			it("팀 메타와 구성원 현황(자신 ACTIVE, 대상 INVITED)을 반환한다 (200)") {
				val ownerId = 3001L
				val invitedUserId = 3002L
				val teamId: Long = inviteTeam(ownerId, invitedUserId)

				get("/teams/v1/invitation") {
					bearer(accessTokenFor(ownerId))
				} expect {
					status(200)
					body("success", true)
					body("data.teamId", teamId.toInt())
					body("data.name", "우리팀")
					body("data.introduction", "함께 즐겁게 활동할 팀이에요")
					body("data.status", TeamStatus.INVITING.name)
					body("data.members", hasSize<Any>(2))
					body("data.members.userId", containsInAnyOrder(ownerId.toInt(), invitedUserId.toInt()))
					body("data.members.status", containsInAnyOrder(TeamMemberStatus.ACTIVE.name, TeamMemberStatus.INVITED.name))
				}
			}
		}

		context("초대받은 유저가 조회하면") {
			it("data가 null이다 (200)") {
				val ownerId = 3003L
				val invitedUserId = 3004L
				inviteTeam(ownerId, invitedUserId)

				get("/teams/v1/invitation") {
					bearer(accessTokenFor(invitedUserId))
				} expect {
					status(200)
					body("success", true)
					body("data", nullValue())
				}
			}
		}

		context("진행 중인 초대가 없는 유저가 조회하면") {
			it("data가 null이다 (200)") {
				val userId = 3005L
				persistMatchUser(userId, Gender.MALE)

				get("/teams/v1/invitation") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("success", true)
					body("data", nullValue())
				}
			}
		}

		context("초대를 철회한 뒤 초대자가 조회하면") {
			it("data가 null이다 (200)") {
				val ownerId = 3006L
				val invitedUserId = 3007L
				val teamId: Long = inviteTeam(ownerId, invitedUserId)

				delete("/teams/v1/$teamId/invitation") {
					bearer(accessTokenFor(ownerId))
				} expect {
					status(200)
				}

				get("/teams/v1/invitation") {
					bearer(accessTokenFor(ownerId))
				} expect {
					status(200)
					body("success", true)
					body("data", nullValue())
				}
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				get("/teams/v1/invitation") expect {
					status(401)
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
```

- [ ] **Step 4: E2E 테스트 실행 (통과 확인)**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.match.GetSentInvitationE2ETest"`
Expected: BUILD SUCCESSFUL — 5개 테스트(초대자 조회 / 초대받은 유저 null / 초대 없음 null / 철회 후 null / 미인증 401) 모두 PASS

- [ ] **Step 5: Commit**

```bash
git add meeple-api/src/main/kotlin/com/org/meeple/api/match/response/SentInvitationResponse.kt \
        meeple-api/src/main/kotlin/com/org/meeple/api/match/TeamController.kt \
        meeple-api/src/test/kotlin/com/org/meeple/api/match/GetSentInvitationE2ETest.kt
git commit -m "feat: 내가 보낸 초대 현황 조회 엔드포인트(GET /teams/v1/invitation)"
```

---

## Self-Review

**Spec coverage:**
- `GET /teams/v1/invitation` 단일 조회 → Task 2 Step 2 ✓
- ACTIVE 구성원·INVITING·최근 1건 → Task 1 Step 5 (where + orderBy desc + limit 1) ✓
- 접근 제어(초대자에게만) → Task 1 쿼리 조건으로 충족, Task 2 Step 3 테스트(초대받은 유저·비구성원 null) ✓
- 없을 때 null(200) → Task 2 Step 2 `?.let`, Step 3 테스트 ✓
- 최소 정보 응답(teamId/name/introduction/status + members userId·status) → Task 1 Step 1 DTO, Task 2 Step 1 응답 ✓
- 인덱스 신규 없음 → Task 1 Step 5 주석으로 명시, 기존 인덱스 사용 ✓
- 테스트(E2E 4 시나리오 + 인증) → Task 2 Step 3 ✓

**Placeholder scan:** 없음. 모든 코드 블록은 실제 구현.

**Type consistency:**
- `SentInvitation(teamId, name, introduction, status, members)` — Task 1 정의 ↔ Task 2 `SentInvitationResponse.of` 사용 일치 ✓
- `SentInvitationMember(userId, status)` — Task 1 정의 ↔ Task 2 `.map` 사용 일치 ✓
- `GetSentInvitationUseCase.get(requesterId): SentInvitation?` — Task 1 정의 ↔ Task 2 컨트롤러 호출 일치 ✓
- `GetSentInvitationDao.findLatestInviting(requesterId)` — Task 1 service ↔ daoImpl 일치 ✓
