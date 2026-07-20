# 회원 탈퇴(10일 유예·복구·파기) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 로그인 사용자가 계정을 탈퇴하면 즉시 비활성(소프트삭제)하고, 10일 내 같은 소셜로 재로그인하면 계정을 복구하며, 10일 경과분은 배치가 개인정보를 익명화(파기)한다.

**Architecture:** 헥사고날. 탈퇴는 `oneulsogae-api` 컨트롤러 → core `WithdrawUserUseCase`. 복구는 기존 OAuth 로그인 진입점 core `RegisterUserService`에 분기 추가. 파기는 `oneulsogae-scheduler` 배치(Job/Service + 자체 out-port) → infra Bridge 어댑터가 core `PurgeWithdrawnUserUseCase`에 위임(기존 `ExpireMatch` 배치와 동형). 소프트삭제 행을 다루는 조회/갱신은 `@SQLRestriction("deleted_at is null")` 우회를 위해 **네이티브 쿼리**로 구현한다.

**Tech Stack:** Kotlin 2.2.21 / JVM 21, Spring Boot 4, Spring Data JPA(MySQL), Kotest(도메인 유닛), Testcontainers + RestAssured(E2E).

## Global Constraints

- 응답·주석·커밋 메시지는 한국어. 커밋 형식 `<type>(user): <설명>`.
- `oneulsogae-backend`만 수정. 프론트엔드 변경 금지(필요 사항은 안내만).
- 타입 명시(변수·반환·람다 파라미터). `LocalDateTime.now()` 직접 호출 금지 — core는 `TimeGenerator`, scheduler는 자체 `TimeGenerator` 주입.
- 모듈 의존 방향 준수: 컨트롤러는 in-port만 주입. core는 out-port 정의, infra가 구현. scheduler는 core에 의존하지 않고 자체 out-port만 두며 infra Bridge가 잇는다.
- 소프트삭제 행 접근은 네이티브 쿼리(`nativeQuery = true`)로 `@SQLRestriction` 우회. 기존 `@Modifying(clearAutomatically = true)` 패턴을 따른다.
- E2E/통합 테스트는 `AbstractIntegrationSupport` + `IntegrationUtil`/엔티티 픽스처 사용. 테스트에서 리포지토리 직접 의존 금지(픽스처/IntegrationUtil/주입된 UseCase 빈만 사용).
- 설계 문서: `docs/superpowers/specs/2026-07-01-user-withdrawal-design.md`.

---

## 파일 구조 (생성/수정 맵)

**Phase A 탈퇴**
- core: `WithdrawUserUseCase`(in), `WithdrawUserService`, `SoftDeleteUserPort`(out), `RevokeUserTokensPort`(out)
- infra: `UserJpaRepository`(+native softDelete), `UserRepositoryAdapter`(+SoftDeleteUserPort), `RefreshTokenRevokeAdapter`(RevokeUserTokensPort)
- api: `UserAccountController`
- test: `WithdrawAccountE2ETest`

**Phase B 복구**
- core: `GetUserPort`(+findWithdrawnUserId), `RestoreUserPort`(out), `RegisterUserService`(+복구 분기)
- infra: `UserJpaRepository`(+native findWithdrawnId/restoreById), `UserRepositoryAdapter`(+두 메서드)
- test: `RestoreAccountOnLoginE2ETest`(통합)

**Phase C 파기**
- common: `UserStatus`(+WITHDRAWN)
- core: `PurgeWithdrawnUserUseCase`(in), `PurgeWithdrawnUserService`, `AnonymizeUserPort`(out), `AnonymizeUserDetailPort`(out)
- infra: `UserJpaRepository`(+native anonymizeById/findPurgableUserIds), `UserRepositoryAdapter`(+AnonymizeUserPort, +GetPurgableWithdrawnUserPort), `UserDetailEntity`(+anonymize()), `UserDetailCoreAdapter`(+AnonymizeUserDetailPort), `PurgeWithdrawnUserBridgeAdapter`
- scheduler: `RunPurgeWithdrawnUserBatchUseCase`(in), `PurgeWithdrawnUserBatchService`, `PurgeWithdrawnUserBatchJob`, `PurgeWithdrawnUserBatchResult`, `GetPurgableWithdrawnUserPort`(out), `PurgeWithdrawnUserPort`(out)
- api: `PurgeWithdrawnUserBatchScheduler`, `application.yml`(cron + retention-days)
- test: `UserStatusTest`(유닛), `PurgeWithdrawnUserE2ETest`(통합)

**공통 수정**
- `UserEntity`: `providerId` `val → var`

---

## Phase A — 탈퇴 (동기)

### Task A1: 탈퇴 out-port와 infra 구현

**Files:**
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/application/port/out/SoftDeleteUserPort.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/application/port/out/RevokeUserTokensPort.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/command/repository/UserJpaRepository.kt`
- Modify: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/command/adapter/UserRepositoryAdapter.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/auth/adapter/RefreshTokenRevokeAdapter.kt`

**Interfaces:**
- Produces: `SoftDeleteUserPort.softDelete(userId: Long, at: LocalDateTime)`, `RevokeUserTokensPort.revokeAll(userId: Long)`, `UserJpaRepository.softDeleteById(id, now): Int`

- [ ] **Step 1: out-port 2개 작성**

`SoftDeleteUserPort.kt`:
```kotlin
package com.org.oneulsogae.core.user.command.application.port.out

import java.time.LocalDateTime

/** 사용자 계정을 소프트삭제(비활성)하는 아웃포트. 탈퇴 유예의 시작점이며 데이터는 보존된다. */
interface SoftDeleteUserPort {

	/** [userId] 사용자의 users 행 deleted_at을 [at]으로 설정한다. (이미 삭제된 행은 변경 없음) */
	fun softDelete(userId: Long, at: LocalDateTime)
}
```

`RevokeUserTokensPort.kt`:
```kotlin
package com.org.oneulsogae.core.user.command.application.port.out

/** 사용자의 모든 인증 토큰(refresh token)을 폐기하는 아웃포트. */
interface RevokeUserTokensPort {

	/** [userId]의 모든 유효 refresh token을 폐기한다. */
	fun revokeAll(userId: Long)
}
```

- [ ] **Step 2: UserJpaRepository에 네이티브 softDelete 추가**

`UserJpaRepository.kt` (import 추가 + 메서드):
```kotlin
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
```
인터페이스 본문에 추가:
```kotlin
	/** 소프트삭제(탈퇴): deleted_at을 설정한다. @SQLRestriction 우회를 위해 네이티브. */
	@Modifying(clearAutomatically = true)
	@Query(value = "update users set deleted_at = :now where id = :id and deleted_at is null", nativeQuery = true)
	fun softDeleteById(@Param("id") id: Long, @Param("now") now: LocalDateTime): Int
```

- [ ] **Step 3: UserRepositoryAdapter가 SoftDeleteUserPort 구현**

`UserRepositoryAdapter.kt`: 클래스 선언에 `SoftDeleteUserPort` 추가, import 추가, 메서드 추가:
```kotlin
import com.org.oneulsogae.core.user.command.application.port.out.SoftDeleteUserPort
import java.time.LocalDateTime
```
```kotlin
class UserRepositoryAdapter(
	private val userJpaRepository: UserJpaRepository,
) : GetUserPort, SaveUserPort, SoftDeleteUserPort {
```
본문 추가:
```kotlin
	override fun softDelete(userId: Long, at: LocalDateTime) {
		userJpaRepository.softDeleteById(userId, at)
	}
```

- [ ] **Step 4: RefreshTokenRevokeAdapter 작성 (RevokeUserTokensPort 구현)**

`RefreshTokenRevokeAdapter.kt`:
```kotlin
package com.org.oneulsogae.infra.auth.adapter

import com.org.oneulsogae.core.user.command.application.port.out.RevokeUserTokensPort
import com.org.oneulsogae.infra.auth.repository.RefreshTokenRepository
import org.springframework.stereotype.Component

/** [RevokeUserTokensPort] 구현. 사용자의 모든 유효 refresh token을 폐기한다. */
@Component
class RefreshTokenRevokeAdapter(
	private val refreshTokenRepository: RefreshTokenRepository,
) : RevokeUserTokensPort {

	override fun revokeAll(userId: Long) {
		refreshTokenRepository.revokeAllByUserId(userId)
	}
}
```

- [ ] **Step 5: 컴파일 확인**

Run: `./gradlew :oneulsogae-core:compileKotlin :oneulsogae-infra:compileKotlin -q`
Expected: 성공(출력 없음).

- [ ] **Step 6: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/application/port/out/SoftDeleteUserPort.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/application/port/out/RevokeUserTokensPort.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/command/repository/UserJpaRepository.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/user/command/adapter/UserRepositoryAdapter.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/auth/adapter/RefreshTokenRevokeAdapter.kt
git commit -m "feat(user): 탈퇴용 소프트삭제·토큰폐기 아웃포트와 어댑터 추가"
```

---

### Task A2: WithdrawUserService (탈퇴 유스케이스)

**Files:**
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/application/port/in/WithdrawUserUseCase.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/application/WithdrawUserService.kt`

**Interfaces:**
- Consumes: `GetUserPort.findById`, `SoftDeleteUserPort.softDelete`, `RevokeUserTokensPort.revokeAll`, `SyncMatchUserUseCase.sync(userId, null)`, `TimeGenerator.now()`
- Produces: `WithdrawUserUseCase.withdraw(userId: Long)`

- [ ] **Step 1: in-port 작성**

`WithdrawUserUseCase.kt`:
```kotlin
package com.org.oneulsogae.core.user.command.application.port.`in`

/** 회원 탈퇴 유스케이스. 계정을 비활성(소프트삭제)하고 토큰 폐기·매칭 풀 제거를 수행한다. (데이터는 보존, 10일 내 복구 가능) */
interface WithdrawUserUseCase {

	fun withdraw(userId: Long)
}
```

- [ ] **Step 2: Service 작성**

`WithdrawUserService.kt`:
```kotlin
package com.org.oneulsogae.core.user.command.application

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.common.time.TimeGenerator
import com.org.oneulsogae.core.matchuser.command.application.port.`in`.SyncMatchUserUseCase
import com.org.oneulsogae.core.user.UserErrorCode
import com.org.oneulsogae.core.user.command.application.port.`in`.WithdrawUserUseCase
import com.org.oneulsogae.core.user.command.application.port.out.GetUserPort
import com.org.oneulsogae.core.user.command.application.port.out.RevokeUserTokensPort
import com.org.oneulsogae.core.user.command.application.port.out.SoftDeleteUserPort
import com.org.oneulsogae.core.user.command.domain.User
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [WithdrawUserUseCase] 구현.
 * 계정을 소프트삭제(비활성)하되 프로필·코인 등 데이터는 보존한다(10일 유예 후 배치가 파기).
 * 보안을 위해 토큰을 폐기하고, 매칭 읽기 모델(match_user)에서 즉시 제거해 매칭 풀에서 빠지게 한다.
 */
@Service
class WithdrawUserService(
	private val getUserPort: GetUserPort,
	private val softDeleteUserPort: SoftDeleteUserPort,
	private val revokeUserTokensPort: RevokeUserTokensPort,
	private val syncMatchUserUseCase: SyncMatchUserUseCase,
	private val timeGenerator: TimeGenerator,
) : WithdrawUserUseCase {

	@Transactional
	override fun withdraw(userId: Long) {
		// 이미 탈퇴(소프트삭제)한 계정은 조회되지 않으므로 USER_NOT_FOUND로 자연 차단된다.
		getUserPort.findById(userId)
			?: throw BusinessException(UserErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다: $userId")

		val now: LocalDateTime = timeGenerator.now()
		softDeleteUserPort.softDelete(userId, now)
		revokeUserTokensPort.revokeAll(userId)
		syncMatchUserUseCase.sync(userId, null)
	}
}
```
(주의: `User` import는 미사용이면 제거. 위 코드에서는 사용하지 않으므로 import하지 않는다.)

수정 후 import 정리: `com.org.oneulsogae.core.user.command.domain.User` 줄과 `LocalDateTime`만 실제 사용분으로 둔다(`User` 미사용 → 삭제).

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :oneulsogae-core:compileKotlin -q`
Expected: 성공.

- [ ] **Step 4: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/application/port/in/WithdrawUserUseCase.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/application/WithdrawUserService.kt
git commit -m "feat(user): 회원 탈퇴 유스케이스(WithdrawUserService) 추가"
```

---

### Task A3: 탈퇴 엔드포인트 + E2E

**Files:**
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/user/UserAccountController.kt`
- Create: `oneulsogae-infra/src/testFixtures/kotlin/com/org/oneulsogae/infra/fixture/RefreshTokenEntityFixture.kt` (현재 없음 → 생성)
- Create: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/user/WithdrawAccountE2ETest.kt`

**Interfaces:**
- Consumes: `WithdrawUserUseCase.withdraw`, `TokenCookieFactory.expiredAccessTokenCookie()/expiredRefreshTokenCookie()`, `@LoginUser AuthUser`

> 확인된 하니스: `delete(path){}` DSL은 `oneulsogae-api/src/test/.../common/integration/RestAssuredDsl.kt`에 존재. `MatchUserEntityFixture` 존재. `AbstractIntegrationSupport`는 `SpringExtension` 등록으로 생성자 `@Autowired` 주입 지원(B2·C5에서 사용). **`RefreshTokenEntityFixture`는 없음 → 아래 Step 0에서 생성.**

- [ ] **Step 0: RefreshTokenEntityFixture 생성**

`RefreshTokenEntityFixture.kt`:
```kotlin
package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.infra.auth.entity.RefreshTokenEntity
import java.time.LocalDateTime

/** [RefreshTokenEntity] 테스트 픽스처. tokenId는 ux_token_id 유니크 제약이 있어 여러 개 만들 땐 달리한다. */
object RefreshTokenEntityFixture {

	fun create(
		userId: Long = 1L,
		tokenId: String = "test-token-id",
		expiresAt: LocalDateTime = LocalDateTime.now().plusDays(14),
		revoked: Boolean = false,
	): RefreshTokenEntity =
		RefreshTokenEntity(
			tokenId = tokenId,
			userId = userId,
			expiresAt = expiresAt,
			revoked = revoked,
		)
}
```

- [ ] **Step 1: E2E 테스트 작성 (실패 확인용)**

`WithdrawAccountE2ETest.kt`:
```kotlin
package com.org.oneulsogae.api.user

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.user.UserStatus
import com.org.oneulsogae.common.match.MatchStatus
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.MatchUserEntityFixture
import com.org.oneulsogae.infra.fixture.RefreshTokenEntityFixture
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.auth.entity.QRefreshTokenEntity
import com.org.oneulsogae.infra.matchuser.command.entity.QMatchUserEntity
import com.org.oneulsogae.infra.user.command.entity.QUserEntity
import io.kotest.matchers.shouldBe

/**
 * `DELETE /users/v1/account` E2E.
 * 탈퇴 시 계정이 소프트삭제(조회 불가)되고, refresh token이 폐기되며, 매칭 읽기모델에서 제거되는지 검증한다.
 */
class WithdrawAccountE2ETest : AbstractIntegrationSupport({

	describe("DELETE /users/v1/account") {

		context("로그인 사용자가 탈퇴를 요청하면") {
			it("계정이 소프트삭제되고 토큰 폐기·매칭 제거된다 (200)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ACTIVE),
				).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId))
				IntegrationUtil.persist(MatchUserEntityFixture.create(userId = userId))
				IntegrationUtil.persist(RefreshTokenEntityFixture.create(userId = userId))

				delete("/users/v1/account") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("success", true)
				}

				// 소프트삭제 → 일반 조회(@SQLRestriction)에서 제외
				activeUserCountOf(userId) shouldBe 0
				// 매칭 읽기모델 제거(하드삭제)
				matchUserCountOf(userId) shouldBe 0
				// refresh token 폐기
				validRefreshTokenCountOf(userId) shouldBe 0
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QRefreshTokenEntity.refreshTokenEntity)
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}
})

private fun activeUserCountOf(userId: Long): Int =
	IntegrationUtil.getQuery()
		.selectFrom(QUserEntity.userEntity)
		.where(QUserEntity.userEntity.id.eq(userId))
		.fetch().size

private fun matchUserCountOf(userId: Long): Int =
	IntegrationUtil.getQuery()
		.selectFrom(QMatchUserEntity.matchUserEntity)
		.where(QMatchUserEntity.matchUserEntity.userId.eq(userId))
		.fetch().size

private fun validRefreshTokenCountOf(userId: Long): Int =
	IntegrationUtil.getQuery()
		.selectFrom(QRefreshTokenEntity.refreshTokenEntity)
		.where(
			QRefreshTokenEntity.refreshTokenEntity.userId.eq(userId),
			QRefreshTokenEntity.refreshTokenEntity.revoked.isFalse,
		)
		.fetch().size
```

> 사전 확인: `delete { }` DSL 헬퍼, `RefreshTokenEntityFixture`, `MatchUserEntityFixture`가 `oneulsogae-infra` testFixtures와 `common.integration`에 있는지 확인한다. **없으면** 이 Task에서 함께 추가한다: `delete`는 기존 `post`/`get`(`com.org.oneulsogae.common.integration`)과 동일 시그니처로 `RestAssured`의 DELETE를 호출하는 최상위 함수, `RefreshTokenEntityFixture.create(userId, revoked = false)`/`MatchUserEntityFixture.create(userId)`는 각 엔티티의 NOT NULL 필드를 기본값으로 채우는 팩토리.

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.user.WithdrawAccountE2ETest"`
Expected: FAIL (엔드포인트 없음 → 404/컴파일 에러 또는 빨강).

- [ ] **Step 3: 컨트롤러 구현**

`UserAccountController.kt`:
```kotlin
package com.org.oneulsogae.api.user

import com.org.oneulsogae.auth.AuthUser
import com.org.oneulsogae.auth.LoginUser
import com.org.oneulsogae.auth.jwt.TokenCookieFactory
import com.org.oneulsogae.core.common.response.ApiResponse
import com.org.oneulsogae.core.user.command.application.port.`in`.WithdrawUserUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "회원", description = "회원 계정 관리(탈퇴 등)")
@RestController
@RequestMapping("/users/v1/account")
class UserAccountController(
	private val withdrawUserUseCase: WithdrawUserUseCase,
	private val tokenCookieFactory: TokenCookieFactory,
) {

	/** 회원 탈퇴: 계정을 비활성(소프트삭제)하고 인증 쿠키를 삭제한다. 10일 내 같은 소셜로 재로그인하면 복구된다. */
	@Operation(summary = "회원 탈퇴", description = "계정을 비활성화하고 토큰·쿠키를 폐기한다. 10일 내 재로그인 시 복구된다.")
	@DeleteMapping
	fun withdraw(@LoginUser user: AuthUser, response: HttpServletResponse): ApiResponse<Unit> {
		withdrawUserUseCase.withdraw(user.id)
		response.addHeader(HttpHeaders.SET_COOKIE, tokenCookieFactory.expiredAccessTokenCookie().toString())
		response.addHeader(HttpHeaders.SET_COOKIE, tokenCookieFactory.expiredRefreshTokenCookie().toString())
		return ApiResponse.success()
	}
}
```

- [ ] **Step 4: 테스트 실행 → 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.user.WithdrawAccountE2ETest"`
Expected: PASS (4 tests… 실제 1 컨텍스트). BUILD SUCCESSFUL.

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/user/UserAccountController.kt \
        oneulsogae-infra/src/testFixtures/kotlin/com/org/oneulsogae/infra/fixture/RefreshTokenEntityFixture.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/user/WithdrawAccountE2ETest.kt
git commit -m "feat(user): 회원 탈퇴 엔드포인트(DELETE /users/v1/account) 추가"
```

---

## Phase B — 복구 (OAuth 재로그인 시 자동)

### Task B1: 복구 조회·복구 out-port와 infra 구현

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/application/port/out/GetUserPort.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/user/command/application/port/out/RestoreUserPort.kt`
- Modify: `oneulsogae-infra/.../user/command/repository/UserJpaRepository.kt`
- Modify: `oneulsogae-infra/.../user/command/adapter/UserRepositoryAdapter.kt`

**Interfaces:**
- Produces: `GetUserPort.findWithdrawnUserId(provider, providerId): Long?`, `RestoreUserPort.restore(userId, at): User`, `UserJpaRepository.findWithdrawnId(...)`, `UserJpaRepository.restoreById(id, now): Int`

- [ ] **Step 1: GetUserPort에 메서드 추가**

`GetUserPort.kt` 본문에 추가:
```kotlin
	/** 소프트삭제(탈퇴 유예중) 사용자를 원본 provider/providerId로 찾는다. 파기된 행은 provider_id가 치환돼 잡히지 않는다. */
	fun findWithdrawnUserId(provider: String, providerId: String): Long?
```

- [ ] **Step 2: RestoreUserPort 작성**

`RestoreUserPort.kt`:
```kotlin
package com.org.oneulsogae.core.user.command.application.port.out

import com.org.oneulsogae.core.user.command.domain.User
import java.time.LocalDateTime

/** 소프트삭제된 사용자를 복구(deleted_at 해제)하는 아웃포트. */
interface RestoreUserPort {

	/** [userId] 사용자의 deleted_at을 해제하고 last_login_at을 [at]으로 갱신한 뒤, 복구된 도메인 모델을 반환한다. */
	fun restore(userId: Long, at: LocalDateTime): User
}
```

- [ ] **Step 3: UserJpaRepository 네이티브 메서드 추가**

```kotlin
	/** 소프트삭제 포함 조회: 탈퇴 유예중(원본 provider_id 잔존) 사용자 id. */
	@Query(value = "select id from users where provider = :provider and provider_id = :providerId and deleted_at is not null", nativeQuery = true)
	fun findWithdrawnId(@Param("provider") provider: String, @Param("providerId") providerId: String): Long?

	/** 복구: deleted_at 해제 + last_login_at 갱신. */
	@Modifying(clearAutomatically = true)
	@Query(value = "update users set deleted_at = null, last_login_at = :now where id = :id", nativeQuery = true)
	fun restoreById(@Param("id") id: Long, @Param("now") now: LocalDateTime): Int
```

- [ ] **Step 4: UserRepositoryAdapter 구현**

클래스 선언에 `RestoreUserPort` 추가. 메서드 추가:
```kotlin
	override fun findWithdrawnUserId(provider: String, providerId: String): Long? =
		userJpaRepository.findWithdrawnId(provider, providerId)

	override fun restore(userId: Long, at: LocalDateTime): User {
		userJpaRepository.restoreById(userId, at)
		// 복구 후에는 @SQLRestriction을 통과하므로 일반 조회로 도메인 모델을 읽는다.
		return userJpaRepository.findById(userId).orElseThrow().toDomain()
	}
```

- [ ] **Step 5: 컴파일 확인**

Run: `./gradlew :oneulsogae-core:compileKotlin :oneulsogae-infra:compileKotlin -q`
Expected: 성공.

- [ ] **Step 6: 커밋**

```bash
git add oneulsogae-core/.../port/out/GetUserPort.kt \
        oneulsogae-core/.../port/out/RestoreUserPort.kt \
        oneulsogae-infra/.../repository/UserJpaRepository.kt \
        oneulsogae-infra/.../adapter/UserRepositoryAdapter.kt
git commit -m "feat(user): 탈퇴 계정 복구용 조회·복구 아웃포트와 어댑터 추가"
```

---

### Task B2: RegisterUserService 복구 분기 + 통합 테스트

**Files:**
- Modify: `oneulsogae-core/.../application/RegisterUserService.kt`
- Create: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/user/RestoreAccountOnLoginE2ETest.kt`

**Interfaces:**
- Consumes: `GetUserPort.findWithdrawnUserId`, `RestoreUserPort.restore`, `DomainEventPublisher.publish(UserProfileChanged)`, `RegisterUserUseCase.registerIfAbsent`

- [ ] **Step 1: 통합 테스트 작성 (실패 확인용)**

`RestoreAccountOnLoginE2ETest.kt`:
```kotlin
package com.org.oneulsogae.api.user

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.user.UserStatus
import com.org.oneulsogae.core.user.command.application.port.`in`.RegisterUserUseCase
import com.org.oneulsogae.core.user.command.application.port.`in`.WithdrawUserUseCase
import com.org.oneulsogae.core.user.command.domain.User
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.matchuser.command.entity.QMatchUserEntity
import com.org.oneulsogae.infra.user.command.entity.QUserEntity
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired

/**
 * 탈퇴 후 같은 provider/providerId로 재로그인(registerIfAbsent) 시 계정이 복구되는지 검증한다.
 * (OAuth 결선은 CustomOAuth2UserService → registerIfAbsent로 동일하므로 유스케이스 레벨로 검증한다)
 */
class RestoreAccountOnLoginE2ETest(
	@Autowired private val registerUserUseCase: RegisterUserUseCase,
	@Autowired private val withdrawUserUseCase: WithdrawUserUseCase,
) : AbstractIntegrationSupport({

	describe("탈퇴 후 같은 소셜로 재로그인") {

		it("기존 계정이 복구된다 (같은 id, deleted_at 해제, status 보존)") {
			val userId: Long = IntegrationUtil.persist(
				UserEntityFixture.create(
					provider = "kakao",
					providerId = "kakao-123",
					email = "u@test.com",
					status = UserStatus.ACTIVE,
				),
			).id!!
			IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId))

			withdrawUserUseCase.withdraw(userId)
			activeUserCountOf(userId) shouldBe 0   // 소프트삭제됨

			val restored: User = registerUserUseCase.registerIfAbsent("kakao", "kakao-123", "u@test.com", null)

			restored.id shouldBe userId            // 새 계정이 아니라 복구
			restored.status shouldBe UserStatus.ACTIVE
			activeUserCountOf(userId) shouldBe 1   // 다시 조회 가능
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QMatchUserEntity.matchUserEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}
})

private fun activeUserCountOf(userId: Long): Int =
	IntegrationUtil.getQuery()
		.selectFrom(QUserEntity.userEntity)
		.where(QUserEntity.userEntity.id.eq(userId))
		.fetch().size
```

> 확인: `AbstractIntegrationSupport`가 생성자 주입(@Autowired) 스펙을 지원하는지 확인. 미지원이면 `IntegrationUtil`의 빈 조회 헬퍼 또는 `SpringExtension`으로 주입한다(기존 스펙 중 빈 주입 사례를 따른다).

- [ ] **Step 2: 테스트 실행 → 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.user.RestoreAccountOnLoginE2ETest"`
Expected: FAIL (`restored.id`가 새 id이거나 EMAIL_ALREADY_REGISTERED 예외 — 복구 분기 없음).

- [ ] **Step 3: RegisterUserService에 복구 분기 추가**

import 추가:
```kotlin
import com.org.oneulsogae.core.user.command.application.port.out.RestoreUserPort
import com.org.oneulsogae.core.user.command.domain.event.UserProfileChanged
```
생성자에 `RestoreUserPort` 주입:
```kotlin
	private val restoreUserPort: RestoreUserPort,
```
`registerIfAbsent` 본문에서 기존 사용자 분기 직후(이메일 검증 전)에 복구 분기 삽입:
```kotlin
		val existing: User? = getUserPort.findByProviderAndProviderId(provider, providerId)
		if (existing != null) {
			return recordLogin(existing)
		}

		// 탈퇴 유예중(소프트삭제) 계정이면 복구한다. (10일 경계는 파기 배치가 provider_id 치환으로 강제)
		val withdrawnId: Long? = getUserPort.findWithdrawnUserId(provider, providerId)
		if (withdrawnId != null) {
			val restored: User = restoreUserPort.restore(withdrawnId, timeGenerator.now())
			// 복구된 사용자를 매칭 읽기모델에 재적재한다(매칭 가능하면). UserLoggedIn은 기존 행만 갱신하므로 ProfileChanged로 전체 동기화.
			domainEventPublisher.publish(UserProfileChanged(restored.id))
			return restored
		}

		// 신규 가입은 이메일이 반드시 있어야 한다. ...(기존 로직 유지)
```

- [ ] **Step 4: 테스트 실행 → 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.user.RestoreAccountOnLoginE2ETest"`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-core/.../application/RegisterUserService.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/user/RestoreAccountOnLoginE2ETest.kt
git commit -m "feat(user): OAuth 재로그인 시 탈퇴 유예 계정 복구 분기 추가"
```

---

## Phase C — 파기 배치 (10일 경과)

### Task C1: UserStatus.WITHDRAWN + 불변식 유닛 테스트

**Files:**
- Modify: `oneulsogae-common/src/main/kotlin/com/org/oneulsogae/common/user/UserStatus.kt`
- Create: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/user/UserStatusTest.kt`

**Interfaces:**
- Produces: `UserStatus.WITHDRAWN` (isRegistered=false, isMatchable=false)

- [ ] **Step 1: 유닛 테스트 작성**

`UserStatusTest.kt`:
```kotlin
package com.org.oneulsogae.domain.user

import com.org.oneulsogae.common.user.UserStatus
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/** [UserStatus] 불변식: 파기 종단 상태 WITHDRAWN은 정식가입·매칭 대상이 아니다. */
class UserStatusTest : DescribeSpec({

	describe("WITHDRAWN") {
		it("정식 가입 상태가 아니다") { UserStatus.WITHDRAWN.isRegistered() shouldBe false }
		it("매칭 대상이 아니다") { UserStatus.WITHDRAWN.isMatchable() shouldBe false }
	}
})
```

- [ ] **Step 2: 실행 → 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.user.UserStatusTest"`
Expected: FAIL (WITHDRAWN 미존재 → 컴파일 에러).

- [ ] **Step 3: enum 값 추가**

`UserStatus.kt`의 `ACTIVE,` 다음 줄에 추가:
```kotlin
	/** 정식 가입까지 완료한 활성 사용자. */
	ACTIVE,

	/** 탈퇴 유예 경과로 파기(익명화)된 종단 상태. 정식가입·매칭 대상이 아니다. */
	WITHDRAWN,
```
(`isRegistered()`는 `== ACTIVE`, `isMatchable()`은 `ACTIVE || COMPANY_NOT_RESOLVED` 그대로 → WITHDRAWN은 둘 다 false. 수정 불필요.)

- [ ] **Step 4: 실행 → 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.user.UserStatusTest"`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-common/.../user/UserStatus.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/user/UserStatusTest.kt
git commit -m "feat(user): 파기 종단 상태 UserStatus.WITHDRAWN 추가"
```

---

### Task C2: 파기 익명화 — core UseCase/ports + infra

**Files:**
- Modify: `oneulsogae-infra/.../user/command/entity/UserEntity.kt` (`providerId` `val → var`)
- Modify: `oneulsogae-infra/.../user/command/entity/UserDetailEntity.kt` (`anonymize()` 추가)
- Create: `oneulsogae-core/.../user/command/application/port/in/PurgeWithdrawnUserUseCase.kt`
- Create: `oneulsogae-core/.../user/command/application/PurgeWithdrawnUserService.kt`
- Create: `oneulsogae-core/.../user/command/application/port/out/AnonymizeUserPort.kt`
- Create: `oneulsogae-core/.../user/command/application/port/out/AnonymizeUserDetailPort.kt`
- Modify: `oneulsogae-infra/.../user/command/repository/UserJpaRepository.kt` (native anonymizeById)
- Modify: `oneulsogae-infra/.../user/command/adapter/UserRepositoryAdapter.kt` (AnonymizeUserPort)
- Modify: `oneulsogae-infra/.../user/command/adapter/UserDetailCoreAdapter.kt` (AnonymizeUserDetailPort)

**Interfaces:**
- Produces: `PurgeWithdrawnUserUseCase.purge(userId: Long)`, `AnonymizeUserPort.anonymize(userId, anonymizedProviderId)`, `AnonymizeUserDetailPort.anonymize(userId, at)`

- [ ] **Step 1: UserEntity.providerId 가변화**

`UserEntity.kt`:
```kotlin
	@Column(name = "provider_id", nullable = false)
	var providerId: String,
```
(`val` → `var`만 변경.)

- [ ] **Step 2: UserDetailEntity.anonymize() 추가**

`UserDetailEntity.kt` 클래스 본문(메서드)에 추가:
```kotlin
	/** 모든 프로필 개인정보(PII)를 제거한다. (파기 단계 — id/userId만 남는다) */
	fun anonymize() {
		nickname = null
		profileImageCode = null
		birthday = null
		height = null
		gender = null
		phoneNumber = null
		job = null
		regionId = null
		introduction = null
		traits = emptyList()
		interests = emptyList()
		companyEmail = null
		companyName = null
		universityEmail = null
		universityName = null
		maritalStatus = null
		smokingStatus = null
		religion = null
		drinkingStatus = null
		bodyType = null
	}
```
(모든 필드가 `var`인지 확인. 아니면 해당 필드를 `var`로 — 이미 모두 var.)

- [ ] **Step 3: core out-port 2개 작성**

`AnonymizeUserPort.kt`:
```kotlin
package com.org.oneulsogae.core.user.command.application.port.out

/** 파기: users 행의 개인정보를 익명화하는 아웃포트(소프트삭제 행 대상, 네이티브). */
interface AnonymizeUserPort {

	/** [userId]의 email=null, provider_id=[anonymizedProviderId], status=WITHDRAWN으로 익명화한다. */
	fun anonymize(userId: Long, anonymizedProviderId: String)
}
```

`AnonymizeUserDetailPort.kt`:
```kotlin
package com.org.oneulsogae.core.user.command.application.port.out

import java.time.LocalDateTime

/** 파기: user_details 행의 개인정보(PII)를 제거하고 소프트삭제하는 아웃포트. */
interface AnonymizeUserDetailPort {

	/** [userId] 프로필의 PII를 모두 제거하고 deleted_at을 [at]으로 설정한다. (프로필이 없으면 무시) */
	fun anonymize(userId: Long, at: LocalDateTime)
}
```

- [ ] **Step 4: in-port + Service 작성**

`PurgeWithdrawnUserUseCase.kt`:
```kotlin
package com.org.oneulsogae.core.user.command.application.port.`in`

/** 탈퇴 유예가 지난 사용자 1명의 개인정보를 익명화(파기)하는 유스케이스. (배치가 사용자별로 호출) */
interface PurgeWithdrawnUserUseCase {

	fun purge(userId: Long)
}
```

`PurgeWithdrawnUserService.kt`:
```kotlin
package com.org.oneulsogae.core.user.command.application

import com.org.oneulsogae.core.common.time.TimeGenerator
import com.org.oneulsogae.core.user.command.application.port.`in`.PurgeWithdrawnUserUseCase
import com.org.oneulsogae.core.user.command.application.port.out.AnonymizeUserDetailPort
import com.org.oneulsogae.core.user.command.application.port.out.AnonymizeUserPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [PurgeWithdrawnUserUseCase] 구현. 탈퇴 유예가 지난 사용자의 개인정보를 익명화한다.
 * provider_id를 "withdrawn_{userId}"(유일·결정적)로 치환해 (provider, provider_id) 유니크를 풀고 복구 불가로 만든다.
 * 코인 원장 등 법령 보존 데이터는 건드리지 않는다(user_id 링크는 익명화된 user를 가리켜 비식별).
 */
@Service
class PurgeWithdrawnUserService(
	private val anonymizeUserPort: AnonymizeUserPort,
	private val anonymizeUserDetailPort: AnonymizeUserDetailPort,
	private val timeGenerator: TimeGenerator,
) : PurgeWithdrawnUserUseCase {

	@Transactional
	override fun purge(userId: Long) {
		anonymizeUserPort.anonymize(userId, "withdrawn_$userId")
		anonymizeUserDetailPort.anonymize(userId, timeGenerator.now())
	}
}
```

- [ ] **Step 5: UserJpaRepository 네이티브 anonymize 추가**

```kotlin
	/** 파기: users 익명화. (소프트삭제 행 대상 → 네이티브) */
	@Modifying(clearAutomatically = true)
	@Query(value = "update users set email = null, provider_id = :providerId, status = 'WITHDRAWN' where id = :id", nativeQuery = true)
	fun anonymizeById(@Param("id") id: Long, @Param("providerId") providerId: String): Int
```

- [ ] **Step 6: 어댑터 구현**

`UserRepositoryAdapter.kt`: 클래스 선언에 `AnonymizeUserPort` 추가, 메서드:
```kotlin
	override fun anonymize(userId: Long, anonymizedProviderId: String) {
		userJpaRepository.anonymizeById(userId, anonymizedProviderId)
	}
```

`UserDetailCoreAdapter.kt`: 클래스 선언에 `AnonymizeUserDetailPort` 추가, import + 메서드:
```kotlin
import com.org.oneulsogae.core.user.command.application.port.out.AnonymizeUserDetailPort
import java.time.LocalDateTime
```
```kotlin
	override fun anonymize(userId: Long, at: LocalDateTime) {
		// 파기 시점의 user_details는 아직 deleted_at이 null이라 일반 조회로 로드된다.
		val entity = userDetailJpaRepository.findByUserId(userId) ?: return
		entity.anonymize()
		entity.softDelete(at)
		userDetailJpaRepository.save(entity)
	}
```

- [ ] **Step 7: 컴파일 확인**

Run: `./gradlew :oneulsogae-core:compileKotlin :oneulsogae-infra:compileKotlin -q`
Expected: 성공.

- [ ] **Step 8: 커밋**

```bash
git add oneulsogae-infra/.../entity/UserEntity.kt oneulsogae-infra/.../entity/UserDetailEntity.kt \
        oneulsogae-core/.../port/in/PurgeWithdrawnUserUseCase.kt \
        oneulsogae-core/.../application/PurgeWithdrawnUserService.kt \
        oneulsogae-core/.../port/out/AnonymizeUserPort.kt oneulsogae-core/.../port/out/AnonymizeUserDetailPort.kt \
        oneulsogae-infra/.../repository/UserJpaRepository.kt \
        oneulsogae-infra/.../adapter/UserRepositoryAdapter.kt oneulsogae-infra/.../adapter/UserDetailCoreAdapter.kt
git commit -m "feat(user): 탈퇴 계정 파기(익명화) 유스케이스와 어댑터 추가"
```

---

### Task C3: scheduler 파기 배치 (Job/Service/ports)

**Files:**
- Create: `oneulsogae-scheduler/.../user/command/application/port/in/RunPurgeWithdrawnUserBatchUseCase.kt`
- Create: `oneulsogae-scheduler/.../user/command/application/port/out/GetPurgableWithdrawnUserPort.kt`
- Create: `oneulsogae-scheduler/.../user/command/application/port/out/PurgeWithdrawnUserPort.kt`
- Create: `oneulsogae-scheduler/.../user/command/domain/PurgeWithdrawnUserBatchResult.kt`
- Create: `oneulsogae-scheduler/.../user/command/application/PurgeWithdrawnUserBatchService.kt`
- Create: `oneulsogae-scheduler/.../user/command/adapter/PurgeWithdrawnUserBatchJob.kt`

> 패키지 베이스: `com.org.oneulsogae.scheduler.user.command...`

**Interfaces:**
- Consumes: scheduler `TimeGenerator`(`com.org.oneulsogae.scheduler.common.command.application.port.out.TimeGenerator`)
- Produces: `RunPurgeWithdrawnUserBatchUseCase.run(): PurgeWithdrawnUserBatchResult`, `GetPurgableWithdrawnUserPort.findUserIdsWithdrawnBefore(cutoff): List<Long>`, `PurgeWithdrawnUserPort.purge(userId)`, `PurgeWithdrawnUserBatchJob.run()`

- [ ] **Step 1: out-port 2개 + result + in-port 작성**

`GetPurgableWithdrawnUserPort.kt`:
```kotlin
package com.org.oneulsogae.scheduler.user.command.application.port.out

import java.time.LocalDateTime

/** 파기 대상(유예 경과·미파기) 사용자 id를 조회하는 아웃포트. (구현은 infra, 소프트삭제 행이라 네이티브) */
interface GetPurgableWithdrawnUserPort {

	/** deleted_at이 [cutoff] 이전이고 아직 익명화(WITHDRAWN)되지 않은 사용자 id 목록. */
	fun findUserIdsWithdrawnBefore(cutoff: LocalDateTime): List<Long>
}
```

`PurgeWithdrawnUserPort.kt`:
```kotlin
package com.org.oneulsogae.scheduler.user.command.application.port.out

/** 사용자 1명을 파기(익명화)하는 아웃포트. 구현은 infra 브리지가 core 유스케이스에 위임한다. */
interface PurgeWithdrawnUserPort {

	fun purge(userId: Long)
}
```

`PurgeWithdrawnUserBatchResult.kt`:
```kotlin
package com.org.oneulsogae.scheduler.user.command.domain

/** 파기 배치 결과 집계. */
data class PurgeWithdrawnUserBatchResult(
	val purged: Int,
	val failed: Int,
)
```

`RunPurgeWithdrawnUserBatchUseCase.kt`:
```kotlin
package com.org.oneulsogae.scheduler.user.command.application.port.`in`

import com.org.oneulsogae.scheduler.user.command.domain.PurgeWithdrawnUserBatchResult

/** 탈퇴 유예 경과 사용자 파기 배치 실행 유스케이스(in-port). */
interface RunPurgeWithdrawnUserBatchUseCase {

	fun run(): PurgeWithdrawnUserBatchResult
}
```

- [ ] **Step 2: Service 작성**

`PurgeWithdrawnUserBatchService.kt`:
```kotlin
package com.org.oneulsogae.scheduler.user.command.application

import com.org.oneulsogae.scheduler.common.command.application.port.out.TimeGenerator
import com.org.oneulsogae.scheduler.user.command.application.port.`in`.RunPurgeWithdrawnUserBatchUseCase
import com.org.oneulsogae.scheduler.user.command.application.port.out.GetPurgableWithdrawnUserPort
import com.org.oneulsogae.scheduler.user.command.application.port.out.PurgeWithdrawnUserPort
import com.org.oneulsogae.scheduler.user.command.domain.PurgeWithdrawnUserBatchResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * [RunPurgeWithdrawnUserBatchUseCase] 구현. 탈퇴 유예([retentionDays]일)가 지난 사용자의 개인정보를 익명화한다.
 * 대상 id를 한 번 적재하고 건별로 [PurgeWithdrawnUserPort]에 위임한다(사용자당 트랜잭션 1개).
 * 한 건의 실패가 다른 건에 전파되지 않게 격리하고 예외만 failed로 집계한다.
 */
@Service
class PurgeWithdrawnUserBatchService(
	private val getPurgableWithdrawnUserPort: GetPurgableWithdrawnUserPort,
	private val purgeWithdrawnUserPort: PurgeWithdrawnUserPort,
	private val timeGenerator: TimeGenerator,
	@Value("\${oneulsogae.user.withdrawal.retention-days:10}") private val retentionDays: Long,
) : RunPurgeWithdrawnUserBatchUseCase {

	private val log: Logger = LoggerFactory.getLogger(javaClass)

	override fun run(): PurgeWithdrawnUserBatchResult {
		val cutoff: LocalDateTime = timeGenerator.now().minusDays(retentionDays)

		var purged = 0
		var failed = 0
		for (userId: Long in getPurgableWithdrawnUserPort.findUserIdsWithdrawnBefore(cutoff)) {
			try {
				purgeWithdrawnUserPort.purge(userId)
				purged++
			} catch (e: Exception) {
				failed++
				log.warn("탈퇴 계정 파기 실패 userId={}", userId, e)
			}
		}

		val result = PurgeWithdrawnUserBatchResult(purged = purged, failed = failed)
		log.info("탈퇴 계정 파기 배치 완료: {}", result)
		return result
	}
}
```

- [ ] **Step 3: Job(진입점) 작성**

`PurgeWithdrawnUserBatchJob.kt`:
```kotlin
package com.org.oneulsogae.scheduler.user.command.adapter

import com.org.oneulsogae.scheduler.user.command.application.port.`in`.RunPurgeWithdrawnUserBatchUseCase
import com.org.oneulsogae.scheduler.user.command.domain.PurgeWithdrawnUserBatchResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

/** 탈퇴 계정 파기 배치 실행 진입점. 프로세스 내 가드로 중복 실행을 막는다. */
@Component
class PurgeWithdrawnUserBatchJob(
	private val runPurgeWithdrawnUserBatchUseCase: RunPurgeWithdrawnUserBatchUseCase,
) {

	private val log: Logger = LoggerFactory.getLogger(javaClass)
	private val running: AtomicBoolean = AtomicBoolean(false)

	fun run(): PurgeWithdrawnUserBatchResult? {
		if (!running.compareAndSet(false, true)) {
			log.warn("탈퇴 계정 파기 배치가 이미 실행 중이라 이번 트리거는 건너뜁니다.")
			return null
		}
		return try {
			log.info("탈퇴 계정 파기 배치 시작")
			runPurgeWithdrawnUserBatchUseCase.run()
		} finally {
			running.set(false)
		}
	}
}
```

- [ ] **Step 4: 컴파일 확인**

Run: `./gradlew :oneulsogae-scheduler:compileKotlin -q`
Expected: 성공.

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-scheduler/src/main/kotlin/com/org/oneulsogae/scheduler/user/
git commit -m "feat(scheduler): 탈퇴 계정 파기 배치(Job/Service/port) 추가"
```

---

### Task C4: infra 브리지·조회 DAO + api 스케줄러 + 설정

**Files:**
- Modify: `oneulsogae-infra/.../user/command/repository/UserJpaRepository.kt` (native findPurgableUserIds)
- Create: `oneulsogae-infra/.../user/command/adapter/GetPurgableWithdrawnUserDaoImpl.kt`
- Create: `oneulsogae-infra/.../user/command/adapter/PurgeWithdrawnUserBridgeAdapter.kt`
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/scheduler/user/PurgeWithdrawnUserBatchScheduler.kt`
- Modify: `oneulsogae-api/src/main/resources/application.yml`

**Interfaces:**
- Consumes: `GetPurgableWithdrawnUserPort`, `PurgeWithdrawnUserPort`(scheduler out-ports), `PurgeWithdrawnUserUseCase`(core in-port), `PurgeWithdrawnUserBatchJob`

- [ ] **Step 1: UserJpaRepository 파기 대상 조회 추가**

```kotlin
	/** 파기 대상: 유예 경과(deleted_at < cutoff) + 아직 미익명화(status <> WITHDRAWN). 네이티브. */
	@Query(value = "select id from users where deleted_at is not null and deleted_at < :cutoff and status <> 'WITHDRAWN'", nativeQuery = true)
	fun findPurgableUserIds(@Param("cutoff") cutoff: LocalDateTime): List<Long>
```

- [ ] **Step 2: 조회 DAO 작성 (scheduler out-port 구현)**

`GetPurgableWithdrawnUserDaoImpl.kt`:
```kotlin
package com.org.oneulsogae.infra.user.command.adapter

import com.org.oneulsogae.infra.user.command.repository.UserJpaRepository
import com.org.oneulsogae.scheduler.user.command.application.port.out.GetPurgableWithdrawnUserPort
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/** [GetPurgableWithdrawnUserPort] 구현. 소프트삭제 행을 다루므로 네이티브 조회를 사용한다. */
@Component
class GetPurgableWithdrawnUserDaoImpl(
	private val userJpaRepository: UserJpaRepository,
) : GetPurgableWithdrawnUserPort {

	override fun findUserIdsWithdrawnBefore(cutoff: LocalDateTime): List<Long> =
		userJpaRepository.findPurgableUserIds(cutoff)
}
```

- [ ] **Step 3: 브리지 어댑터 작성 (scheduler → core 위임)**

`PurgeWithdrawnUserBridgeAdapter.kt`:
```kotlin
package com.org.oneulsogae.infra.user.command.adapter

import com.org.oneulsogae.core.user.command.application.port.`in`.PurgeWithdrawnUserUseCase
import com.org.oneulsogae.scheduler.user.command.application.port.out.PurgeWithdrawnUserPort
import org.springframework.stereotype.Component

/** scheduler [PurgeWithdrawnUserPort]를 core [PurgeWithdrawnUserUseCase]에 잇는 브리지. (트랜잭션 경계는 core 서비스가 가짐) */
@Component
class PurgeWithdrawnUserBridgeAdapter(
	private val purgeWithdrawnUserUseCase: PurgeWithdrawnUserUseCase,
) : PurgeWithdrawnUserPort {

	override fun purge(userId: Long) {
		purgeWithdrawnUserUseCase.purge(userId)
	}
}
```

- [ ] **Step 4: api 스케줄러(크론 트리거) 작성**

`PurgeWithdrawnUserBatchScheduler.kt`:
```kotlin
package com.org.oneulsogae.scheduler.user

import com.org.oneulsogae.scheduler.user.command.adapter.PurgeWithdrawnUserBatchJob
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** 탈퇴 계정 파기 배치 스케줄러(크론 트리거). 주기는 oneulsogae.user.withdrawal.purge-batch.cron으로 조정한다. */
@Component
class PurgeWithdrawnUserBatchScheduler(
	private val purgeWithdrawnUserBatchJob: PurgeWithdrawnUserBatchJob,
) {

	@Scheduled(cron = "\${oneulsogae.user.withdrawal.purge-batch.cron}", zone = "Asia/Seoul")
	fun runPurge() {
		purgeWithdrawnUserBatchJob.run()
	}
}
```

- [ ] **Step 5: application.yml에 설정 추가**

운영 프로파일 블록(예: `oneulsogae.match` 인근, line ~73-83 영역)과 같은 레벨에 `oneulsogae.user` 트리를 추가한다. **두 프로파일(운영/로컬·테스트) 모두**에 동일 키를 추가한다.

운영 기본값(`ONEULSOGAE_*` 환경변수 기본):
```yaml
  user:
    withdrawal:
      retention-days: 10
      purge-batch:
        cron: ${ONEULSOGAE_PURGE_WITHDRAWN_BATCH_CRON:0 0 5 * * *}   # 매일 05:00 KST
```
로컬/테스트 프로파일(line ~116-124 영역, 분 단위 등 기존 컨벤션):
```yaml
  user:
    withdrawal:
      retention-days: 10
      purge-batch:
        cron: ${ONEULSOGAE_PURGE_WITHDRAWN_BATCH_CRON:0 * * * * *}
```
> 정확한 들여쓰기 레벨은 기존 `oneulsogae:` 루트 아래 `match:`와 동일하게 맞춘다.

- [ ] **Step 6: 컴파일 확인**

Run: `./gradlew :oneulsogae-infra:compileKotlin :oneulsogae-api:compileKotlin -q`
Expected: 성공.

- [ ] **Step 7: 커밋**

```bash
git add oneulsogae-infra/.../repository/UserJpaRepository.kt \
        oneulsogae-infra/.../adapter/GetPurgableWithdrawnUserDaoImpl.kt \
        oneulsogae-infra/.../adapter/PurgeWithdrawnUserBridgeAdapter.kt \
        oneulsogae-api/src/main/kotlin/com/org/oneulsogae/scheduler/user/PurgeWithdrawnUserBatchScheduler.kt \
        oneulsogae-api/src/main/resources/application.yml
git commit -m "feat(scheduler): 탈퇴 계정 파기 배치 결선(브리지·DAO·크론) 추가"
```

---

### Task C5: 파기 동작 통합 테스트

**Files:**
- Create: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/user/PurgeWithdrawnUserE2ETest.kt`

**Interfaces:**
- Consumes: `PurgeWithdrawnUserBatchJob.run()`, `RegisterUserUseCase.registerIfAbsent`

- [ ] **Step 1: 통합 테스트 작성**

10일 이전에 탈퇴(소프트삭제)한 사용자를 만들고 배치를 돌린 뒤, **같은 provider/providerId로 재로그인하면 복구가 아니라 새 계정이 생성**되는지로 파기(provider_id 치환·복구 불가)를 행위로 검증한다. (소프트삭제·익명화 행은 QueryDSL로 직접 못 읽으므로 행위 기반 검증)

`PurgeWithdrawnUserE2ETest.kt`:
```kotlin
package com.org.oneulsogae.api.user

import com.org.oneulsogae.common.user.UserStatus
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.core.user.command.application.port.`in`.RegisterUserUseCase
import com.org.oneulsogae.core.user.command.domain.User
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.user.command.entity.QUserEntity
import com.org.oneulsogae.scheduler.user.command.adapter.PurgeWithdrawnUserBatchJob
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

/**
 * 유예(10일) 경과 탈퇴 계정 파기 배치 검증.
 * 11일 전 소프트삭제된 사용자를 배치가 익명화하면, 같은 카카오로 재로그인 시 복구되지 않고 새 계정이 생성된다.
 */
class PurgeWithdrawnUserE2ETest(
	@Autowired private val purgeWithdrawnUserBatchJob: PurgeWithdrawnUserBatchJob,
	@Autowired private val registerUserUseCase: RegisterUserUseCase,
) : AbstractIntegrationSupport({

	describe("탈퇴 계정 파기 배치") {

		it("유예 경과분을 익명화해 복구 불가·새 계정 가입이 된다") {
			// 11일 전에 탈퇴(소프트삭제)된 사용자. softDelete(at)으로 deleted_at을 과거로 박아 insert.
			val withdrawnAt: LocalDateTime = LocalDateTime.now().minusDays(11)
			val oldId: Long = IntegrationUtil.persist(
				UserEntityFixture.create(
					provider = "kakao",
					providerId = "kakao-777",
					email = "old@test.com",
					status = UserStatus.ACTIVE,
				).also { it.softDelete(withdrawnAt) },
			).id!!
			IntegrationUtil.persist(UserDetailEntityFixture.create(userId = oldId))

			purgeWithdrawnUserBatchJob.run()

			// 파기 후: provider_id가 치환돼 복구 대상이 아니므로 같은 카카오로 신규 가입된다.
			val newUser: User = registerUserUseCase.registerIfAbsent("kakao", "kakao-777", "new@test.com", null)
			newUser.id shouldNotBe oldId
			newUser.status shouldBe UserStatus.ONBOARDING   // 복구가 아닌 신규 → 온보딩부터
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QUserEntity.userEntity)   // @SQLRestriction으로 익명화 행이 안 지워질 수 있으니 주의(아래 노트)
	}
})
```
> 정리 노트: 익명화된 행은 `deleted_at`이 남아 `QUserEntity` 기반 `deleteAll`(@SQLRestriction)로 안 지워질 수 있다. `IntegrationUtil`에 소프트삭제 포함 정리 헬퍼가 없다면, 본 스펙의 정리는 테스트 컨테이너 격리에 의존하거나 별도 native 정리 헬퍼를 추가한다(기존 정리 패턴 확인 후 택일).

- [ ] **Step 2: 테스트 실행 → 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.user.PurgeWithdrawnUserE2ETest"`
Expected: PASS.

- [ ] **Step 3: 전체 user 관련 테스트 회귀 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.user.*" --tests "com.org.oneulsogae.domain.user.*"`
Expected: PASS.

- [ ] **Step 4: 커밋**

```bash
git add oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/user/PurgeWithdrawnUserE2ETest.kt
git commit -m "test(user): 탈퇴 계정 파기 배치 통합 테스트 추가"
```

---

## 최종 검증

- [ ] **전체 빌드/테스트**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **설계 문서와 대조** (spec coverage): 탈퇴(A)·복구(B)·파기(C)·코인 보존(미변경)·재가입(파기 후 새 계정)·status 보존·네이티브 우회 모두 태스크로 커버됨을 확인.

---

## 프론트엔드 안내 (백엔드 외 — 직접 수정하지 않음)

- 신규 `DELETE /users/v1/account` 연동(탈퇴 버튼). 성공 시 클라이언트 토큰/세션 정리 후 로그인 화면 이동.
- 탈퇴 후 10일 내 같은 소셜 로그인 시 계정이 복구된다는 안내 문구 권장.
- 신규 에러코드 없음(미존재/이미탈퇴는 404 `USER-001`).
