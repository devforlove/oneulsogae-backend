# 내가 받은 초대 리스트 조회 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 초대받은(INVITED) 유저가 자신에게 온 대기 중(INVITING) 초대들을 초대자 프로필과 함께 최신순으로 조회하는 `GET /teams/v1/received-invitations` 엔드포인트를 추가한다.

**Architecture:** 헥사고날 CQRS query 슬라이스. core query에 read model(중첩 DTO)·in-port·out-port·service, infra query에 QueryDSL daoImpl(self-join으로 초대자 식별 + match_user·user_details 프로필 조인), api에 응답 DTO·컨트롤러 메서드를 둔다. 기존 `SentInvitation`/`GetSentInvitation` 슬라이스 패턴을 그대로 따른다.

**Tech Stack:** Kotlin 2.2.21 / Spring Boot 4.0.6 / Spring Data JPA / QueryDSL / Kotest(E2E) + Testcontainers.

## Global Constraints

- 타입 명시: 변수·반환 타입·람다 파라미터 타입을 생략하지 않는다.
- query는 command 도메인·포트를 참조하지 않고 자기 dao에만 의존한다.
- 조회 서비스는 `@Transactional(readOnly = true)`. 컨트롤러는 in-port `UseCase`를 주입한다.
- 요청자가 INVITED 구성원인 INVITING 팀만, team id desc 최신순, 전체(페이지 없음).
- 각 항목 = 팀 메타(teamId·name·introduction) + 초대자(owner = ACTIVE 구성원) 프로필(userId·nickname·job·companyName·gender·profileImageCode·age). job·companyName은 nullable.
- 없으면 빈 배열(HTTP 200). 인덱스 신규 추가 없음(기존 idx_user_id·ux_team_id_user_id·ux_user_id·PK로 seek; @SQLRestriction이 소프트 삭제 제외).
- 탭 들여쓰기. POST 초대 경로는 `/teams/v1/invitation`(현재 워킹트리 기준).

---

## File Structure

| 파일 | 책임 | Task |
|---|---|---|
| `meeple-core/.../match/query/dto/ReceivedInvitation.kt` | read model(`ReceivedInvitation`,`ReceivedInvitationInviter`) | 1 |
| `meeple-core/.../match/query/service/port/in/GetReceivedInvitationsUseCase.kt` | in-port | 1 |
| `meeple-core/.../match/query/dao/GetReceivedInvitationsDao.kt` | query out-port | 1 |
| `meeple-core/.../match/query/service/GetReceivedInvitationsService.kt` | UseCase 구현 | 1 |
| `meeple-infra/.../match/query/GetReceivedInvitationsDaoImpl.kt` | QueryDSL 구현 | 1 |
| `meeple-api/.../match/response/ReceivedInvitationResponse.kt` | 응답 DTO(+nested Inviter) | 2 |
| `meeple-api/.../match/TeamController.kt` | `getReceivedInvitations()` 추가 | 2 |
| `meeple-api/.../test/.../match/GetReceivedInvitationsE2ETest.kt` | E2E | 2 |

---

## Task 1: core query 슬라이스 + infra QueryDSL dao

요청자가 INVITED 구성원인 INVITING 팀들을 초대자 프로필과 함께 조회하는 query 경로를 구현한다. (HTTP 경계는 Task 2)

**Files:**
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/match/query/dto/ReceivedInvitation.kt`
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/match/query/service/port/in/GetReceivedInvitationsUseCase.kt`
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/match/query/dao/GetReceivedInvitationsDao.kt`
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/match/query/service/GetReceivedInvitationsService.kt`
- Create: `meeple-infra/src/main/kotlin/com/org/meeple/infra/match/query/GetReceivedInvitationsDaoImpl.kt`

**Interfaces:**
- Produces (Task 2가 사용):
  - `ReceivedInvitation(teamId: Long, name: String, introduction: String?, inviter: ReceivedInvitationInviter)`
  - `ReceivedInvitationInviter(userId: Long, nickname: String, job: String?, companyName: String?, gender: Gender, profileImageCode: String, age: Int)`
  - `GetReceivedInvitationsUseCase.get(requesterId: Long): List<ReceivedInvitation>`

- [ ] **Step 1: read model DTO 작성**

`meeple-core/src/main/kotlin/com/org/meeple/core/match/query/dto/ReceivedInvitation.kt`:

```kotlin
package com.org.meeple.core.match.query.dto

import com.org.meeple.common.user.Gender

/**
 * 내가 받은 초대 한 건(read model). 초대받은(INVITED) 유저가 보는, 대기 중(INVITING) 팀과 초대자 프로필.
 * query 전용 view이며 command 도메인을 참조하지 않는다.
 */
data class ReceivedInvitation(
	val teamId: Long,
	val name: String,
	val introduction: String?,
	val inviter: ReceivedInvitationInviter,
)

/** 초대자(owner) 프로필. 닉네임·프로필이미지·나이는 match_user, 직업·회사명은 user_details에서 온다. */
data class ReceivedInvitationInviter(
	val userId: Long,
	val nickname: String,
	val job: String?,
	val companyName: String?,
	val gender: Gender,
	val profileImageCode: String,
	val age: Int,
)
```

- [ ] **Step 2: in-port(UseCase) 작성**

`meeple-core/src/main/kotlin/com/org/meeple/core/match/query/service/port/in/GetReceivedInvitationsUseCase.kt`:

```kotlin
package com.org.meeple.core.match.query.service.port.`in`

import com.org.meeple.core.match.query.dto.ReceivedInvitation

/**
 * 내가 받은 초대 리스트를 조회하는 유스케이스(인포트).
 * 요청자가 INVITED 구성원인 INVITING 팀들을 최신순으로 반환한다. 없으면 빈 리스트.
 */
interface GetReceivedInvitationsUseCase {

	/** [requesterId]가 받은(INVITED) 대기 중 초대들을 최신순으로 조회한다. */
	fun get(requesterId: Long): List<ReceivedInvitation>
}
```

- [ ] **Step 3: out-port(Dao) 작성**

`meeple-core/src/main/kotlin/com/org/meeple/core/match/query/dao/GetReceivedInvitationsDao.kt`:

```kotlin
package com.org.meeple.core.match.query.dao

import com.org.meeple.core.match.query.dto.ReceivedInvitation

/**
 * 내가 받은 초대 리스트 조회 dao(query out-port). 실제 QueryDSL 구현은 infra가 담당한다.
 * 요청자가 INVITED 구성원인 INVITING 팀들을 초대자(ACTIVE 구성원) 프로필과 함께 team id desc로 반환한다.
 */
interface GetReceivedInvitationsDao {

	/** [requesterId]가 INVITED 구성원인 INVITING 팀들을 초대자 프로필과 함께 최신순으로 반환한다. */
	fun findInvited(requesterId: Long): List<ReceivedInvitation>
}
```

- [ ] **Step 4: query service 작성**

`meeple-core/src/main/kotlin/com/org/meeple/core/match/query/service/GetReceivedInvitationsService.kt`:

```kotlin
package com.org.meeple.core.match.query.service

import com.org.meeple.core.match.query.dao.GetReceivedInvitationsDao
import com.org.meeple.core.match.query.dto.ReceivedInvitation
import com.org.meeple.core.match.query.service.port.`in`.GetReceivedInvitationsUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetReceivedInvitationsUseCase] 구현. (조회 전용)
 * 요청자가 INVITED 구성원인 INVITING 팀들을 dao로 조회한다. (query dao만 의존, command 포트·도메인 미참조)
 */
@Service
@Transactional(readOnly = true)
class GetReceivedInvitationsService(
	private val getReceivedInvitationsDao: GetReceivedInvitationsDao,
) : GetReceivedInvitationsUseCase {

	override fun get(requesterId: Long): List<ReceivedInvitation> =
		getReceivedInvitationsDao.findInvited(requesterId)
}
```

- [ ] **Step 5: infra QueryDSL daoImpl 작성**

`meeple-infra/src/main/kotlin/com/org/meeple/infra/match/query/GetReceivedInvitationsDaoImpl.kt`:

`me`(나=INVITED)와 `owner`(초대자=ACTIVE)는 같은 team_members를 self-join하므로 **서로 다른 별칭 인스턴스**를 쓴다.

```kotlin
package com.org.meeple.infra.match.query

import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.core.match.query.dao.GetReceivedInvitationsDao
import com.org.meeple.core.match.query.dto.ReceivedInvitation
import com.org.meeple.core.match.query.dto.ReceivedInvitationInviter
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetReceivedInvitationsDao]의 QueryDSL 구현체. (조회 전용)
 * 요청자가 INVITED 구성원(me)인 INVITING 팀을 찾고, 같은 팀의 ACTIVE 구성원(owner=초대자)을 self-join해
 * 초대자 프로필(닉네임·프로필이미지·나이=match_user, 직업·회사명=user_details, 성별=team_members)과 함께 team id desc로 투영한다.
 * 팀당 ACTIVE 구성원은 1명이므로 항목당 1행. [org.hibernate.annotations.SQLRestriction]이 소프트 삭제 행을 제외한다.
 */
@Component
class GetReceivedInvitationsDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetReceivedInvitationsDao {

	override fun findInvited(requesterId: Long): List<ReceivedInvitation> {
		val me: QTeamMemberEntity = QTeamMemberEntity("me")
		val owner: QTeamMemberEntity = QTeamMemberEntity("owner")
		val team: QTeamEntity = QTeamEntity.teamEntity
		val ownerMatch: QMatchUserEntity = QMatchUserEntity.matchUserEntity
		val ownerDetail: QUserDetailEntity = QUserDetailEntity.userDetailEntity

		return queryFactory
			.select(
				Projections.constructor(
					ReceivedInvitation::class.java,
					team.id,
					team.name,
					team.introduction,
					Projections.constructor(
						ReceivedInvitationInviter::class.java,
						owner.userId,
						ownerMatch.nickname,
						ownerDetail.job,
						ownerDetail.companyName,
						owner.gender,
						ownerMatch.profileImageCode,
						ownerMatch.age,
					),
				),
			)
			.from(me)
			.join(team).on(team.id.eq(me.teamId))
			.join(owner).on(owner.teamId.eq(team.id).and(owner.status.eq(TeamMemberStatus.ACTIVE)))
			.join(ownerMatch).on(ownerMatch.userId.eq(owner.userId))
			.join(ownerDetail).on(ownerDetail.userId.eq(owner.userId))
			.where(
				me.userId.eq(requesterId),
				me.status.eq(TeamMemberStatus.INVITED),
				team.status.eq(TeamStatus.INVITING),
			)
			.orderBy(team.id.desc())
			.fetch()
	}
}
```

- [ ] **Step 6: 컴파일 검증**

Run: `./gradlew :meeple-infra:compileKotlin`
Expected: BUILD SUCCESSFUL (meeple-infra가 core에 의존하므로 core query 슬라이스도 함께 컴파일된다)

- [ ] **Step 7: Commit**

```bash
git add meeple-core/src/main/kotlin/com/org/meeple/core/match/query \
        meeple-infra/src/main/kotlin/com/org/meeple/infra/match/query/GetReceivedInvitationsDaoImpl.kt
git commit -m "feat: 받은 초대 리스트 조회 query 슬라이스(core·infra)"
```

---

## Task 2: api 엔드포인트 + E2E 테스트

`GET /teams/v1/received-invitations`를 추가하고 E2E로 동작을 검증한다.

**Files:**
- Create: `meeple-api/src/main/kotlin/com/org/meeple/api/match/response/ReceivedInvitationResponse.kt`
- Modify: `meeple-api/src/main/kotlin/com/org/meeple/api/match/TeamController.kt`
- Create (test): `meeple-api/src/test/kotlin/com/org/meeple/api/match/GetReceivedInvitationsE2ETest.kt`

**Interfaces:**
- Consumes (Task 1에서):
  - `GetReceivedInvitationsUseCase.get(requesterId: Long): List<ReceivedInvitation>`
  - `ReceivedInvitation`, `ReceivedInvitationInviter`

- [ ] **Step 1: 응답 DTO 작성**

`meeple-api/src/main/kotlin/com/org/meeple/api/match/response/ReceivedInvitationResponse.kt`:

```kotlin
package com.org.meeple.api.match.response

import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.query.dto.ReceivedInvitation

/**
 * 내가 받은 초대 한 건 응답. 팀 메타와 초대자(owner) 프로필을 담는다.
 */
data class ReceivedInvitationResponse(
	val teamId: Long,
	val name: String,
	val introduction: String?,
	val inviter: Inviter,
) {

	/** 초대자(owner) 프로필. 닉네임·직업·회사명·성별·프로필이미지·나이. */
	data class Inviter(
		val userId: Long,
		val nickname: String,
		val job: String?,
		val companyName: String?,
		val gender: Gender,
		val profileImageCode: String,
		val age: Int,
	)

	companion object {
		fun of(invitation: ReceivedInvitation): ReceivedInvitationResponse =
			ReceivedInvitationResponse(
				teamId = invitation.teamId,
				name = invitation.name,
				introduction = invitation.introduction,
				inviter = Inviter(
					userId = invitation.inviter.userId,
					nickname = invitation.inviter.nickname,
					job = invitation.inviter.job,
					companyName = invitation.inviter.companyName,
					gender = invitation.inviter.gender,
					profileImageCode = invitation.inviter.profileImageCode,
					age = invitation.inviter.age,
				),
			)
	}
}
```

- [ ] **Step 2: 컨트롤러에 엔드포인트 추가**

`TeamController.kt` 수정:

1. import 추가:
```kotlin
import com.org.meeple.api.match.response.ReceivedInvitationResponse
import com.org.meeple.core.match.query.dto.ReceivedInvitation
import com.org.meeple.core.match.query.service.port.`in`.GetReceivedInvitationsUseCase
```

2. 생성자에 의존성 추가 (`getSentInvitationUseCase` 다음 줄):
```kotlin
	private val getReceivedInvitationsUseCase: GetReceivedInvitationsUseCase,
```

3. 메서드 추가 (`getSentInvitation` 메서드 아래):
```kotlin
	/**
	 * 내가 받은(INVITED) 대기 중 초대 리스트를 최신순으로 조회한다. 각 항목은 팀 메타와 초대자(owner) 프로필을 담는다.
	 */
	@Operation(summary = "받은 초대 리스트 조회", description = "요청자가 INVITED 상태인 INVITING 팀들을 최신순으로 반환한다. 각 항목에 팀 메타와 초대자(owner) 프로필을 담는다.")
	@GetMapping("/received-invitations")
	fun getReceivedInvitations(
		@LoginUser user: AuthUser,
	): ApiResponse<List<ReceivedInvitationResponse>> =
		ApiResponse.success(
			getReceivedInvitationsUseCase.get(user.id).map { invitation: ReceivedInvitation -> ReceivedInvitationResponse.of(invitation) },
		)
```

(주의: `@Operation`은 이미 TeamController에 import되어 있다. `ReceivedInvitation`/`ReceivedInvitationResponse`/`GetReceivedInvitationsUseCase` import만 추가하면 된다.)

- [ ] **Step 3: E2E 테스트 작성**

`meeple-api/src/test/kotlin/com/org/meeple/api/match/GetReceivedInvitationsE2ETest.kt`:

```kotlin
package com.org.meeple.api.match

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.integration.post
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import org.hamcrest.Matchers.hasSize

/**
 * `GET /teams/v1/received-invitations` E2E 테스트. (내가 받은 초대 리스트)
 * 요청자가 INVITED 구성원인 INVITING 팀들을 초대자 프로필과 함께 최신순으로 반환한다.
 * 내가 owner(ACTIVE)인 팀·수락(FORMED)된 팀·미소속 팀은 제외된다.
 */
class GetReceivedInvitationsE2ETest : AbstractIntegrationSupport({

	// 표시용 프로필을 match_user(닉네임·프로필이미지·나이) + user_details(직업·회사명)에 저장한다. (성별 MALE 고정)
	fun persistProfile(userId: Long, nickname: String, profileImageCode: String, job: String?, companyName: String?, age: Int = 28) {
		IntegrationUtil.persist(
			MatchUserEntityFixture.create(userId = userId, gender = Gender.MALE, nickname = nickname, profileImageCode = profileImageCode, age = age),
		)
		IntegrationUtil.persist(
			UserDetailEntityFixture.create(
				userId = userId,
				nickname = nickname,
				gender = Gender.MALE,
				profileImageCode = profileImageCode,
				job = job,
				companyName = companyName,
			),
		)
	}

	// ownerId가 invitedUserId를 초대해 팀을 결성하고 teamId를 돌려준다. (프로필은 미리 저장돼 있어야 한다)
	fun invite(ownerId: Long, invitedUserId: Long): Long =
		post("/teams/v1/invitation") {
			bearer(accessTokenFor(ownerId))
			jsonBody("""{"invitedUserId": $invitedUserId, "name": "우리팀", "introduction": "함께 즐겁게 활동할 팀이에요"}""")
		}.extract().path<Int>("data.teamId").toLong()

	describe("GET /teams/v1/received-invitations") {

		context("초대받은 유저가 여러 초대를 받았으면") {
			it("초대자 프로필을 담아 team id 최신순으로 반환한다 (200)") {
				val me = 4002L
				val ownerA = 4001L
				val ownerB = 4003L
				persistProfile(me, "나", "1", null, null)
				persistProfile(ownerA, "초대자A", "11", "PM", "토스", age = 31)
				persistProfile(ownerB, "초대자B", "22", "디자이너", "카카오", age = 33)

				val teamA: Long = invite(ownerA, me)
				val teamB: Long = invite(ownerB, me)

				get("/teams/v1/received-invitations") {
					bearer(accessTokenFor(me))
				} expect {
					status(200)
					body("success", true)
					body("data", hasSize<Any>(2))
					// 최신순(team id desc) → [0]=teamB(초대자B), [1]=teamA(초대자A)
					body("data[0].teamId", teamB.toInt())
					body("data[0].name", "우리팀")
					body("data[0].inviter.userId", ownerB.toInt())
					body("data[0].inviter.nickname", "초대자B")
					body("data[0].inviter.job", "디자이너")
					body("data[0].inviter.companyName", "카카오")
					body("data[0].inviter.gender", Gender.MALE.name)
					body("data[0].inviter.profileImageCode", "22")
					body("data[0].inviter.age", 33)
					body("data[1].teamId", teamA.toInt())
					body("data[1].inviter.userId", ownerA.toInt())
					body("data[1].inviter.nickname", "초대자A")
				}
			}
		}

		context("초대자(owner) 본인이 조회하면") {
			it("자신은 INVITED가 아니므로 빈 배열이다 (200)") {
				val owner = 4101L
				val invited = 4102L
				persistProfile(owner, "오너", "1", null, null)
				persistProfile(invited, "초대대상", "1", null, null)
				invite(owner, invited)

				get("/teams/v1/received-invitations") {
					bearer(accessTokenFor(owner))
				} expect {
					status(200)
					body("data", hasSize<Any>(0))
				}
			}
		}

		context("초대를 수락(FORMED)한 뒤 조회하면") {
			it("더 이상 INVITED가 아니므로 빈 배열이다 (200)") {
				val owner = 4201L
				val invited = 4202L
				persistProfile(owner, "오너", "1", null, null)
				persistProfile(invited, "초대대상", "1", null, null)
				val teamId: Long = invite(owner, invited)

				post("/teams/v1/$teamId/acceptance") {
					bearer(accessTokenFor(invited))
				} expect {
					status(200)
				}

				get("/teams/v1/received-invitations") {
					bearer(accessTokenFor(invited))
				} expect {
					status(200)
					body("data", hasSize<Any>(0))
				}
			}
		}

		context("받은 초대가 없는 유저가 조회하면") {
			it("빈 배열이다 (200)") {
				val userId = 4301L
				persistProfile(userId, "외톨이", "1", null, null)

				get("/teams/v1/received-invitations") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data", hasSize<Any>(0))
				}
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				get("/teams/v1/received-invitations") expect {
					status(401)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
		IntegrationUtil.deleteAll(QTeamEntity.teamEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
	}
})
```

- [ ] **Step 4: E2E 테스트 실행 (통과 확인)**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.match.GetReceivedInvitationsE2ETest"`
Expected: BUILD SUCCESSFUL — 5개 테스트(여러 초대 최신순+프로필 / owner 빈 배열 / 수락 후 빈 배열 / 없음 빈 배열 / 미인증 401) 모두 PASS

- [ ] **Step 5: Commit**

```bash
git add meeple-api/src/main/kotlin/com/org/meeple/api/match/response/ReceivedInvitationResponse.kt \
        meeple-api/src/main/kotlin/com/org/meeple/api/match/TeamController.kt \
        meeple-api/src/test/kotlin/com/org/meeple/api/match/GetReceivedInvitationsE2ETest.kt
git commit -m "feat: 받은 초대 리스트 조회 엔드포인트(GET /teams/v1/received-invitations)"
```

---

## Self-Review

**Spec coverage:**
- `GET /teams/v1/received-invitations` 리스트 조회 → Task 2 Step 2 ✓
- INVITED 구성원 + INVITING 팀 필터, team id desc → Task 1 Step 5 (where + orderBy desc) ✓
- 초대자(ACTIVE 구성원) self-join + 프로필 조인 → Task 1 Step 5 ✓
- 항목 = 팀 메타 + inviter(중첩) 프로필(7필드) → Task 1 Step 1 DTO, Task 2 Step 1 응답 ✓
- 없을 때 빈 배열 → UseCase가 dao 결과(빈 리스트) 그대로 반환, Task 2 Step 3 테스트 ✓
- 접근 제어(받은 사람만) → 쿼리 조건(me.status=INVITED), Task 2 Step 3 owner 빈 배열 테스트 ✓
- 인덱스 신규 없음 → Task 1 Step 5 주석, 기존 인덱스 사용 ✓
- 테스트(여러 건 정렬·owner 제외·수락 제외·없음·인증) → Task 2 Step 3 ✓

**Placeholder scan:** 없음. 모든 코드 블록은 실제 구현.

**Type consistency:**
- `ReceivedInvitation(teamId, name, introduction, inviter)` / `ReceivedInvitationInviter(userId, nickname, job, companyName, gender, profileImageCode, age)` — Task 1 정의 ↔ Task 2 `ReceivedInvitationResponse.of`·DaoImpl 투영 순서 일치 ✓
- `GetReceivedInvitationsUseCase.get(requesterId): List<ReceivedInvitation>` — Task 1 ↔ Task 2 컨트롤러 호출 일치 ✓
- `GetReceivedInvitationsDao.findInvited(requesterId)` — Task 1 service ↔ daoImpl 일치 ✓
- DaoImpl 중첩 Projection 인자 순서 = `ReceivedInvitation`/`ReceivedInvitationInviter` 생성자 순서와 일치 ✓
