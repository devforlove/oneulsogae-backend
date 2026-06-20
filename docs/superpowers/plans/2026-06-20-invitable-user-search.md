# 초대 가능한 유저 닉네임 검색 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 닉네임이 정확히 일치하면서 초대 가능한(같은 성별·활성 팀 없음·자기 제외·매칭 가능) 유저를 `id·닉네임·직업·회사명`으로 검색하는 조회 엔드포인트를 추가한다.

**Architecture:** match 도메인의 query 슬라이스. 단일 QueryDSL dao가 후보 베이스 `match_user`(매칭 가능 보장)에 `user_details`(표시 필드)를 조인하고 `team_members` NOT EXISTS로 활성 팀 소속을 거른다. query 서비스는 요청자 성별을 user 도메인 in-port로 읽어 dao에 넘긴다. 도메인 모델 로직이 없어 검증은 E2E로 한다.

**Tech Stack:** Kotlin 2.2.21 / Spring Boot 4 / Spring Data JPA / QueryDSL / MySQL · RestAssured + Testcontainers E2E.

## Global Constraints

- 모듈 의존: core는 인프라 비의존. Controller/Request/Response는 meeple-api에만. QueryDSL 구현은 infra.
- CQRS: 조회 서비스 `@Transactional(readOnly = true)`. query는 자기 dao + 타 도메인 in-port만 의존하고 command 도메인/포트를 참조하지 않는다(`GetMatchesService` 선례). 부수효과(저장·상태변경) 금지.
- 조회 read model(DTO/프로젝션)을 반환한다(도메인 모델 금지).
- 타입 명시: 변수·반환·람다 파라미터 타입을 생략하지 않는다.
- 조회 구현 우선순위 ②: QueryDSL(`JPAQueryFactory`만 주입), 명시 조인(`join … on`).
- 닉네임 매칭은 **정확히 일치(equals)**. 결과는 리스트(동명이인 가능). 페이지네이션 없음, 정렬 userId 오름차순.
- 요청자 성별이 null이면 빈 리스트(예외 금지). `UserWithDetailView.getGender()`는 `gender!!`라 호출 금지 — `view.detail.gender`(nullable)를 직접 본다.
- Tabs 들여쓰기; Korean KDoc.
- 테스트: api 경계 → E2E(`AbstractIntegrationSupport` + `IntegrationUtil`/픽스처 + RestAssured DSL). 리포지토리 직접 의존 금지.
- 빌드/테스트: `./gradlew :meeple-api:test --tests "<FQN>"` (Testcontainers MySQL/Redis 기동 — 수 분 소요 정상).

---

## File Structure

**core (match query 슬라이스)**
- Create `meeple-core/.../match/query/dto/InvitableUser.kt` — read model
- Create `meeple-core/.../match/query/dao/SearchInvitableUsersDao.kt` — query out-port(dao) 인터페이스
- Create `meeple-core/.../match/query/service/port/in/SearchInvitableUsersUseCase.kt` — in-port
- Create `meeple-core/.../match/query/service/SearchInvitableUsersService.kt` — 조회 서비스

**infra**
- Create `meeple-infra/.../match/query/SearchInvitableUsersDaoImpl.kt` — QueryDSL 구현

**api**
- Create `meeple-api/.../match/request/SearchInvitableUsersRequest.kt`
- Create `meeple-api/.../match/response/InvitableUserResponse.kt`
- Modify `meeple-api/.../match/TeamController.kt` — `GET /invitable-users`
- Test `meeple-api/src/test/.../api/match/SearchInvitableUsersE2ETest.kt`

---

## Task 1: core 조회 계약 + 서비스

**Files:**
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/match/query/dto/InvitableUser.kt`
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/match/query/dao/SearchInvitableUsersDao.kt`
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/match/query/service/port/in/SearchInvitableUsersUseCase.kt`
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/match/query/service/SearchInvitableUsersService.kt`

**Interfaces:**
- Consumes: `GetUserWithDetailUseCase.getByUserId(userId: Long): UserWithDetailView` (기존, user 도메인 in-port). `UserWithDetailView.detail.gender: Gender?`.
- Produces:
  - `data class InvitableUser(userId: Long, nickname: String, job: String?, companyName: String?)`
  - `SearchInvitableUsersDao.search(requesterGender: Gender, requesterId: Long, nickname: String): List<InvitableUser>`
  - `SearchInvitableUsersUseCase.search(requesterId: Long, nickname: String): List<InvitableUser>`

> 이 태스크는 도메인 모델 로직이 없고(서비스는 dao+DB가 있어야 동작) 유닛 테스트 대상이 아니다. 게이트는 **컴파일 통과**다. 동작은 Task 3 E2E가 검증한다.

- [ ] **Step 1: read model `InvitableUser` 생성**

```kotlin
package com.org.meeple.core.match.query.dto

/**
 * 초대 가능한 유저 검색 결과(read model). 초대 UI에 노출할 식별자·닉네임·직업·회사명을 담는다.
 * query 전용 view이며 command 도메인을 참조하지 않는다.
 */
data class InvitableUser(
	val userId: Long,
	val nickname: String,
	val job: String?,
	val companyName: String?,
)
```

- [ ] **Step 2: dao 인터페이스 `SearchInvitableUsersDao` 생성**

```kotlin
package com.org.meeple.core.match.query.dao

import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.query.dto.InvitableUser

/**
 * 초대 가능한 유저(닉네임 정확 일치) 조회 dao(query out-port). 실제 QueryDSL 구현은 infra가 담당한다.
 * 후보는 매칭 가능(match_user 존재)하고 [requesterGender]와 같은 성별이며, [requesterId] 자신과 활성 팀 소속자는 제외한다.
 */
interface SearchInvitableUsersDao {

	/** [nickname]이 정확히 일치하는 초대 가능 유저 목록을 userId 오름차순으로 반환한다. */
	fun search(requesterGender: Gender, requesterId: Long, nickname: String): List<InvitableUser>
}
```

- [ ] **Step 3: in-port `SearchInvitableUsersUseCase` 생성**

```kotlin
package com.org.meeple.core.match.query.service.port.`in`

import com.org.meeple.core.match.query.dto.InvitableUser

/**
 * 초대 가능한 유저를 닉네임으로 검색하는 유스케이스(인포트).
 * 요청자([requesterId])와 같은 성별·매칭 가능·활성 팀 없음 조건을 만족하는, 닉네임이 정확히 일치하는 유저를 반환한다.
 */
interface SearchInvitableUsersUseCase {

	/** [requesterId]가 [nickname]으로 초대 가능한 유저를 검색한다. */
	fun search(requesterId: Long, nickname: String): List<InvitableUser>
}
```

- [ ] **Step 4: 서비스 `SearchInvitableUsersService` 생성**

```kotlin
package com.org.meeple.core.match.query.service

import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.query.dao.SearchInvitableUsersDao
import com.org.meeple.core.match.query.dto.InvitableUser
import com.org.meeple.core.match.query.service.port.`in`.SearchInvitableUsersUseCase
import com.org.meeple.core.user.query.service.port.`in`.GetUserWithDetailUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [SearchInvitableUsersUseCase] 구현. (조회 전용)
 * 요청자 성별을 user 도메인 in-port([GetUserWithDetailUseCase])로 읽어 dao에 넘긴다. (command 포트 미참조 — query 규칙)
 * 성별이 없으면(매칭 불가) 빈 리스트를 반환한다. ([com.org.meeple.core.user.query.dto.UserWithDetailView.getGender]는 null에서 NPE이므로 호출하지 않고 detail.gender를 직접 본다)
 */
@Service
@Transactional(readOnly = true)
class SearchInvitableUsersService(
	private val getUserWithDetailUseCase: GetUserWithDetailUseCase,
	private val searchInvitableUsersDao: SearchInvitableUsersDao,
) : SearchInvitableUsersUseCase {

	override fun search(requesterId: Long, nickname: String): List<InvitableUser> {
		val requesterGender: Gender = getUserWithDetailUseCase.getByUserId(requesterId).detail.gender
			?: return emptyList()
		return searchInvitableUsersDao.search(requesterGender, requesterId, nickname)
	}
}
```

- [ ] **Step 5: 컴파일 검증**

Run: `./gradlew :meeple-core:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add meeple-core/src/main/kotlin/com/org/meeple/core/match/query/dto/InvitableUser.kt \
  meeple-core/src/main/kotlin/com/org/meeple/core/match/query/dao/SearchInvitableUsersDao.kt \
  meeple-core/src/main/kotlin/com/org/meeple/core/match/query/service/port/in/SearchInvitableUsersUseCase.kt \
  meeple-core/src/main/kotlin/com/org/meeple/core/match/query/service/SearchInvitableUsersService.kt
git commit -m "feat: 초대 가능 유저 검색 조회 계약·서비스(core query) 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: infra QueryDSL dao 구현

**Files:**
- Create: `meeple-infra/src/main/kotlin/com/org/meeple/infra/match/query/SearchInvitableUsersDaoImpl.kt`

**Interfaces:**
- Consumes: `SearchInvitableUsersDao` (Task 1), `InvitableUser` (Task 1). Q-classes: `QMatchUserEntity.matchUserEntity`, `QUserDetailEntity.userDetailEntity`, `QTeamMemberEntity.teamMemberEntity`.

> 게이트는 **컴파일 통과**. 쿼리 동작은 Task 3 E2E가 검증한다.

- [ ] **Step 1: dao 구현 생성**

`MatchUserEntity`(후보, gender 보유)에 `UserDetailEntity`(닉네임·직업·회사명)를 명시 조인하고, `team_members` NOT EXISTS로 활성 팀 소속을 거른다. 세 엔티티 모두 `@SQLRestriction("deleted_at is null")`(match_user는 하드 삭제)라 활성 행만 스캔된다.

```kotlin
package com.org.meeple.infra.match.query

import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.query.dao.SearchInvitableUsersDao
import com.org.meeple.core.match.query.dto.InvitableUser
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [SearchInvitableUsersDao]의 QueryDSL 구현체. (조회 전용)
 * 후보 베이스는 매칭 가능(match_user 존재)을 보장하는 [QMatchUserEntity]이고, 표시 필드(닉네임·직업·회사명)는 [QUserDetailEntity] 조인으로 가져온다.
 * 닉네임 정확 일치 + 같은 성별 + 자기 제외 + 활성 팀 미소속([QTeamMemberEntity] NOT EXISTS)을 만족하는 유저를 userId 오름차순으로 투영한다.
 */
@Component
class SearchInvitableUsersDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : SearchInvitableUsersDao {

	override fun search(requesterGender: Gender, requesterId: Long, nickname: String): List<InvitableUser> {
		val candidate: QMatchUserEntity = QMatchUserEntity.matchUserEntity
		val detail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		val teamMember: QTeamMemberEntity = QTeamMemberEntity.teamMemberEntity

		return queryFactory
			.select(
				Projections.constructor(
					InvitableUser::class.java,
					candidate.userId,
					detail.nickname,
					detail.job,
					detail.companyName,
				),
			)
			.from(candidate)
			.join(detail).on(detail.userId.eq(candidate.userId))
			.where(
				detail.nickname.eq(nickname),
				candidate.gender.eq(requesterGender),
				candidate.userId.ne(requesterId),
				JPAExpressions.selectOne()
					.from(teamMember)
					.where(teamMember.userId.eq(candidate.userId))
					.notExists(),
			)
			.orderBy(candidate.userId.asc())
			.fetch()
	}
}
```

- [ ] **Step 2: 컴파일 검증**

Run: `./gradlew :meeple-infra:compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add meeple-infra/src/main/kotlin/com/org/meeple/infra/match/query/SearchInvitableUsersDaoImpl.kt
git commit -m "feat: 초대 가능 유저 검색 QueryDSL dao 구현

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: api 엔드포인트 + E2E

**Files:**
- Create: `meeple-api/src/main/kotlin/com/org/meeple/api/match/request/SearchInvitableUsersRequest.kt`
- Create: `meeple-api/src/main/kotlin/com/org/meeple/api/match/response/InvitableUserResponse.kt`
- Modify: `meeple-api/src/main/kotlin/com/org/meeple/api/match/TeamController.kt`
- Test: `meeple-api/src/test/kotlin/com/org/meeple/api/match/SearchInvitableUsersE2ETest.kt`

**Interfaces:**
- Consumes: `SearchInvitableUsersUseCase.search(requesterId: Long, nickname: String): List<InvitableUser>` (Task 1).

- [ ] **Step 1: 요청·응답 DTO 생성**

`SearchInvitableUsersRequest.kt` — GET 쿼리 파라미터를 객체로 바인딩(@ModelAttribute)하고 `@Valid`로 검증한다. 공백/누락이면 `MethodArgumentNotValidException`이 발생해 `GlobalExceptionHandler`가 400으로 내린다(본문 요청 검증과 동일 경로).

```kotlin
package com.org.meeple.api.match.request

import jakarta.validation.constraints.NotBlank

/**
 * 초대 가능 유저 검색 요청. 쿼리 파라미터 `nickname`을 바인딩한다.
 * 닉네임은 필수(@NotBlank) — 공백/누락이면 400.
 */
data class SearchInvitableUsersRequest(
	@field:NotBlank(message = "닉네임은 필수입니다.")
	val nickname: String? = null,
)
```

`InvitableUserResponse.kt`:

```kotlin
package com.org.meeple.api.match.response

import com.org.meeple.core.match.query.dto.InvitableUser

/**
 * 초대 가능 유저 검색 결과 항목 응답. 식별자·닉네임·직업·회사명을 담는다.
 */
data class InvitableUserResponse(
	val userId: Long,
	val nickname: String,
	val job: String?,
	val companyName: String?,
) {
	companion object {
		fun of(user: InvitableUser): InvitableUserResponse =
			InvitableUserResponse(
				userId = user.userId,
				nickname = user.nickname,
				job = user.job,
				companyName = user.companyName,
			)
	}
}
```

- [ ] **Step 2: E2E 테스트 작성(실패 확인용)**

Create `meeple-api/src/test/kotlin/com/org/meeple/api/match/SearchInvitableUsersE2ETest.kt`:

```kotlin
package com.org.meeple.api.match

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.MatchUserEntityFixture
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.infra.match.command.entity.TeamMemberEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import org.hamcrest.Matchers.containsInAnyOrder
import org.hamcrest.Matchers.hasSize

/**
 * `GET /teams/v1/invitable-users` E2E 테스트. (초대 가능 유저 닉네임 검색)
 * 후보는 매칭 가능(match_user 존재)·요청자와 같은 성별이고, 자기 자신·반대 성별·활성 팀 소속·매칭 불가 유저는 제외된다.
 */
class SearchInvitableUsersE2ETest : AbstractIntegrationSupport({

	// 매칭 읽기 모델(match_user, 성별 보유) 행을 저장한다.
	fun persistMatchUser(userId: Long, gender: Gender, nickname: String) {
		IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId, gender = gender, nickname = nickname))
	}

	// 프로필 상세(user_details, 닉네임·직업·회사명) 행을 저장한다.
	fun persistUserDetail(userId: Long, gender: Gender, nickname: String, job: String?, companyName: String?) {
		IntegrationUtil.persist(
			UserDetailEntityFixture.create(
				userId = userId,
				nickname = nickname,
				gender = gender,
				job = job,
				companyName = companyName,
			),
		)
	}

	// 활성 팀 구성원 행을 저장한다. (NOT EXISTS 필터 대상 — teamId는 검사에 쓰이지 않아 임의값)
	fun persistActiveTeamMember(userId: Long, gender: Gender) {
		IntegrationUtil.persist(
			TeamMemberEntity(teamId = 1L, userId = userId, gender = gender, status = TeamMemberStatus.ACTIVE),
		)
	}

	describe("GET /teams/v1/invitable-users") {

		context("닉네임이 정확히 일치하는 초대 가능 유저가 있으면") {
			it("같은 성별·매칭가능·팀없음 유저만 id·닉네임·직업·회사명과 함께 반환한다 (200)") {
				val requesterId = 7001L
				val nickname = "홍길동"

				// 요청자: user_details에 성별이 있어야 검색 가능 (닉네임도 동일하지만 자기 자신은 제외돼야 한다)
				persistUserDetail(requesterId, Gender.MALE, nickname, job = "PM", companyName = "내회사")
				persistMatchUser(requesterId, Gender.MALE, nickname)

				// 포함 대상 A, E: 같은 성별(MALE)·매칭가능·팀없음·동일 닉네임 (동명이인 2명)
				persistUserDetail(7002L, Gender.MALE, nickname, job = "개발자", companyName = "토스")
				persistMatchUser(7002L, Gender.MALE, nickname)
				persistUserDetail(7003L, Gender.MALE, nickname, job = null, companyName = null)
				persistMatchUser(7003L, Gender.MALE, nickname)

				// 제외 B: 반대 성별(FEMALE)
				persistUserDetail(7004L, Gender.FEMALE, nickname, job = "디자이너", companyName = "카카오")
				persistMatchUser(7004L, Gender.FEMALE, nickname)

				// 제외 C: 이미 활성 팀 소속
				persistUserDetail(7005L, Gender.MALE, nickname, job = "기획", companyName = "라인")
				persistMatchUser(7005L, Gender.MALE, nickname)
				persistActiveTeamMember(7005L, Gender.MALE)

				// 제외 D: match_user 없음(매칭 불가) — user_details만 존재
				persistUserDetail(7006L, Gender.MALE, nickname, job = "마케터", companyName = "쿠팡")

				// 제외 F: 닉네임 불일치
				persistUserDetail(7007L, Gender.MALE, "임꺽정", job = "개발자", companyName = "배민")
				persistMatchUser(7007L, Gender.MALE, "임꺽정")

				get("/teams/v1/invitable-users?nickname=$nickname") {
					bearer(accessTokenFor(requesterId))
				} expect {
					status(200)
					body("success", true)
					body("data", hasSize<Any>(2))
					body("data.userId", containsInAnyOrder(7002, 7003))
					body("data.job", containsInAnyOrder("개발자", null))
					body("data.companyName", containsInAnyOrder("토스", null))
				}
			}
		}

		context("일치하는 닉네임이 없으면") {
			it("빈 배열을 반환한다 (200)") {
				val requesterId = 7101L
				persistUserDetail(requesterId, Gender.MALE, "내닉네임", job = null, companyName = null)
				persistMatchUser(requesterId, Gender.MALE, "내닉네임")

				get("/teams/v1/invitable-users?nickname=없는닉네임") {
					bearer(accessTokenFor(requesterId))
				} expect {
					status(200)
					body("data", hasSize<Any>(0))
				}
			}
		}

		context("nickname 파라미터가 공백이면") {
			it("400을 반환한다") {
				val requesterId = 7201L
				persistUserDetail(requesterId, Gender.MALE, "내닉네임", job = null, companyName = null)

				get("/teams/v1/invitable-users?nickname=%20%20") {
					bearer(accessTokenFor(requesterId))
				} expect {
					status(400)
					body("success", false)
				}
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				get("/teams/v1/invitable-users?nickname=홍길동") expect {
					status(401)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QTeamMemberEntity.teamMemberEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
	}
})
```

- [ ] **Step 3: 테스트 실행 — 실패 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.match.SearchInvitableUsersE2ETest"`
Expected: 컴파일 실패(엔드포인트·DTO 미존재) 또는 404.

- [ ] **Step 4: TeamController에 엔드포인트 추가**

`TeamController` 생성자에 `searchInvitableUsersUseCase`를 주입하고 GET 매핑을 추가한다. import 추가: `com.org.meeple.core.match.query.service.in.SearchInvitableUsersUseCase`(백틱 `in` 주의), `com.org.meeple.api.match.request.SearchInvitableUsersRequest`, `com.org.meeple.api.match.response.InvitableUserResponse`, `org.springframework.web.bind.annotation.GetMapping`. (`@Valid`, `AuthUser`, `LoginUser`, `ApiResponse`는 이미 import됨)

생성자에 추가:

```kotlin
	private val searchInvitableUsersUseCase: SearchInvitableUsersUseCase,
```

메서드 추가(클래스 본문 안):

```kotlin
	/**
	 * 닉네임이 정확히 일치하는 초대 가능 유저를 검색한다. (같은 성별·매칭 가능·활성 팀 없음·자기 제외)
	 * 결과 항목은 식별자·닉네임·직업·회사명을 담는다.
	 */
	@GetMapping("/invitable-users")
	fun searchInvitableUsers(
		@LoginUser user: AuthUser,
		@Valid request: SearchInvitableUsersRequest,
	): ApiResponse<List<InvitableUserResponse>> =
		ApiResponse.success(
			searchInvitableUsersUseCase.search(user.id, request.nickname!!).map { InvitableUserResponse.of(it) },
		)
```

> import 경로 주의: in-port 패키지는 `com.org.meeple.core.match.query.service.port.in`이고 `in`은 Kotlin 키워드라 백틱이 필요하다 → `import com.org.meeple.core.match.query.service.port.\`in\`.SearchInvitableUsersUseCase`.

- [ ] **Step 5: 테스트 실행 — 통과 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.match.SearchInvitableUsersE2ETest"`
Expected: PASS (4 컨텍스트 전부)

- [ ] **Step 6: Commit**

```bash
git add meeple-api/src/main/kotlin/com/org/meeple/api/match/request/SearchInvitableUsersRequest.kt \
  meeple-api/src/main/kotlin/com/org/meeple/api/match/response/InvitableUserResponse.kt \
  meeple-api/src/main/kotlin/com/org/meeple/api/match/TeamController.kt \
  meeple-api/src/test/kotlin/com/org/meeple/api/match/SearchInvitableUsersE2ETest.kt
git commit -m "feat: 초대 가능 유저 닉네임 검색 엔드포인트(GET /teams/v1/invitable-users)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: 전체 회귀

**Files:** 없음 (검증)

- [ ] **Step 1: meeple-api 전체 테스트**

Run: `./gradlew :meeple-api:test`
Expected: BUILD SUCCESSFUL (신규 E2E + 기존 전부 그린)

- [ ] **Step 2: 전체 빌드**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

---

## Self-Review

**1. Spec coverage**
- match 도메인 query 슬라이스(UseCase/Service/Dao/DTO) → Task 1. ✅
- `match_user` 후보 + `user_details` 조인 + 같은 성별 + 자기 제외 + 활성 팀 NOT EXISTS + 닉네임 정확 일치 + userId 정렬 → Task 2 QueryDSL. ✅
- 요청자 성별 user in-port로 읽기, null이면 빈 리스트(NPE 회피) → Task 1 서비스. ✅
- `GET /teams/v1/invitable-users?nickname=` → `List<InvitableUserResponse{userId,nickname,job,companyName}>` → Task 3. ✅
- 공백/누락 nickname → 400 / 매칭 없음 → 200 빈 배열 / 미인증 → 401 → Task 3 E2E. ✅
- 자기·반대성별·활성팀·비매칭 제외 + 동명이인 리스트 → Task 3 E2E. ✅
- 페이지네이션 없음 / 도메인 유닛 없음(쿼리 슬라이스) → 설계대로. ✅

**2. Placeholder scan**: 모든 스텝에 실제 코드·명령·기대 출력. TODO/TBD 없음. ✅

**3. Type consistency**:
- `search(requesterGender: Gender, requesterId: Long, nickname: String): List<InvitableUser>` — dao 인터페이스(Task 1)·구현(Task 2) 일치. ✅
- `search(requesterId: Long, nickname: String): List<InvitableUser>` — in-port(Task 1)·서비스(Task 1)·컨트롤러(Task 3) 일치. ✅
- `InvitableUser(userId, nickname, job, companyName)` — DTO(Task 1)·Projections(Task 2)·Response.of(Task 3) 필드 순서/타입 일치. ✅
- Q-classes: `QMatchUserEntity`·`QUserDetailEntity`·`QTeamMemberEntity` — 기존 코드에서 사용 확인. ✅
