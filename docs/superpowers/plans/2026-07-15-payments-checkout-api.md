# payments 체크아웃 조회 API 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 결제 화면 진입 시 주문자 정보(실명·이메일·휴대폰)를 반환하는 `GET /payments/v1/checkout` API를 payments 도메인으로 신설한다.

**Architecture:** 헥사고날 + CQRS. payments는 조회 전용이라 `query` 패키지만 둔다. Controller(oneulsogae-api) → in-port `GetCheckoutUseCase` ← `GetCheckoutService`(oneulsogae-core) → dao `GetCheckoutOrdererDao` ← `GetCheckoutOrdererDaoImpl`(oneulsogae-infra, QueryDSL). DaoImpl이 user 계열 엔티티를 직접 투영한다(표시용 조인 패턴 — user 도메인 포트를 거치지 않는다).

**Tech Stack:** Kotlin 2.2.21 / Spring Boot 4.0.6 / QueryDSL(JPAQueryFactory) / Kotest DescribeSpec + Testcontainers + RestAssured DSL

**스펙:** `docs/superpowers/specs/2026-07-15-payments-checkout-api-design.md`

## Global Constraints

- 응답 필드 null 정책: `name`/`email`/`phoneNumber` 모두 nullable, 에러를 던지지 않는다. users 행이 없으면 모든 필드 null.
- `name`은 해당 유저의 `identity_verifications` 중 **최신(id 최대) VERIFIED** 행의 `real_name`.
- 코드 스타일: 탭 들여쓰기, trailing comma, 변수·반환 타입 명시(프로젝트 컨벤션).
- 에러코드·도메인 모델·command 패키지는 만들지 않는다(순수 조회 — YAGNI).
- DaoImpl 조회는 2쿼리로 구현한다: ① users⟕user_details(PK seek) ② identity_verifications `user_id` 동등 + `id desc`(기존 `idx_iv_user_id` 사용, per-user 행 수가 극소라 정렬 비용 무시 가능). 신규 인덱스 불필요.
- 커밋은 E2E GREEN 확인 후 1회: `feat(payments): 체크아웃 주문자 정보 조회 API 추가`.

---

### Task 1: 테스트 픽스처 보강 + 실패하는 E2E 테스트 작성

**Files:**
- Modify: `oneulsogae-infra/src/testFixtures/kotlin/com/org/oneulsogae/infra/fixture/UserDetailEntityFixture.kt` (phoneNumber 파라미터 추가)
- Modify: `oneulsogae-infra/src/testFixtures/kotlin/com/org/oneulsogae/infra/fixture/IdentityVerificationEntityFixture.kt` (realName 파라미터 추가)
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/payments/PaymentsCheckoutE2ETest.kt` (신규)

**Interfaces:**
- Consumes: `AbstractIntegrationSupport`(accessTokenFor), `IntegrationUtil.persist/deleteAll`, RestAssured DSL `get/expect`
- Produces: 없음 (RED 상태의 E2E. Task 4가 이 테스트를 GREEN으로 만든다)

- [ ] **Step 1: UserDetailEntityFixture에 phoneNumber 파라미터 추가**

`UserDetailEntityFixture.create`의 `gender` 파라미터 뒤에 `phoneNumber` 파라미터를 추가하고 생성자에 전달한다:

```kotlin
	fun create(
		userId: Long = 1L,
		nickname: String? = "테스트유저",
		profileImageCode: String? = null,
		gender: Gender? = Gender.FEMALE,
		phoneNumber: String? = null,
		birthday: LocalDate? = LocalDate.of(1996, 1, 1),
		height: Int? = null,
		job: String? = null,
		regionId: Long? = null,
		introduction: String? = null,
		companyEmail: String? = null,
		companyName: String? = null,
		universityEmail: String? = null,
		universityName: String? = null,
	): UserDetailEntity =
		UserDetailEntity(
			userId = userId,
			nickname = nickname,
			profileImageCode = profileImageCode,
			gender = gender,
			phoneNumber = phoneNumber,
			birthday = birthday,
			height = height,
			job = job,
			regionId = regionId,
			introduction = introduction,
			companyEmail = companyEmail,
			companyName = companyName,
			universityEmail = universityEmail,
			universityName = universityName,
		)
```

- [ ] **Step 2: IdentityVerificationEntityFixture에 realName 파라미터 추가**

하드코딩된 `realName = "홍길동"`을 기본값 있는 파라미터로 끌어올린다 (`status` 파라미터 뒤에 추가):

```kotlin
	fun create(
		userId: Long = 1L,
		ordrIdxx: String = "ORD-FIX",
		regCertKey: String = "REG-FIX",
		status: IdentityVerificationStatus = IdentityVerificationStatus.VERIFIED,
		realName: String? = "홍길동",
		phoneNumber: String? = "01012345678",
		di: String? = "DI-FIX",
		birthday: LocalDate? = LocalDate.of(1996, 1, 1),
		gender: Gender? = Gender.MALE,
		verifiedAt: LocalDateTime? = LocalDateTime.of(2026, 7, 9, 12, 0),
	): IdentityVerificationEntity =
		IdentityVerificationEntity(
			userId = userId,
			ordrIdxx = ordrIdxx,
			regCertKey = regCertKey,
			status = status,
			realName = realName,
			birthday = birthday,
			gender = gender,
			phoneNumber = phoneNumber,
			di = di,
			ciEncrypted = "enc",
			foreigner = false,
			telecom = "SKT",
			verifiedAt = verifiedAt,
		)
```

- [ ] **Step 3: E2E 테스트 작성**

`oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/payments/PaymentsCheckoutE2ETest.kt` 전체 내용:

```kotlin
package com.org.oneulsogae.api.payments

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.core.user.command.domain.IdentityVerificationStatus
import com.org.oneulsogae.infra.fixture.IdentityVerificationEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.user.command.entity.QIdentityVerificationEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.org.oneulsogae.infra.user.command.entity.QUserEntity
import com.org.oneulsogae.infra.user.command.entity.UserEntity
import org.hamcrest.Matchers.nullValue

/**
 * `GET /payments/v1/checkout` E2E 테스트.
 *
 * 결제(체크아웃) 화면 진입 시 주문자 정보(실명·이메일·휴대폰)를 반환한다.
 * 실명은 최신 VERIFIED 본인인증 행에서, 이메일은 users, 휴대폰은 user_details에서 읽는다.
 * 주문자 정보 미비는 화면 진입을 막지 않으므로 null 필드로 반환한다(에러 아님).
 */
class PaymentsCheckoutE2ETest : AbstractIntegrationSupport({

	describe("GET /payments/v1/checkout") {

		context("본인인증을 완료한 사용자가 조회하면") {
			it("최신 VERIFIED 인증의 실명과 이메일·휴대폰 번호를 반환한다") {
				val user: UserEntity = IntegrationUtil.persist(
					UserEntityFixture.create(providerId = "checkout-1", email = "orderer@test.com"),
				)
				val userId: Long = user.id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, phoneNumber = "01011112222"))
				// 재인증 이력: 과거 VERIFIED → 최신 VERIFIED → 진행 중(REQUESTED) 순으로 쌓인 상황.
				// 최신 VERIFIED 행("김오늘의 소개")이 선택되어야 하고, REQUESTED 행(실명 없음)은 무시되어야 한다.
				IntegrationUtil.persist(IdentityVerificationEntityFixture.create(userId = userId, realName = "김과거"))
				IntegrationUtil.persist(IdentityVerificationEntityFixture.create(userId = userId, realName = "김오늘의 소개"))
				IntegrationUtil.persist(
					IdentityVerificationEntityFixture.create(
						userId = userId,
						status = IdentityVerificationStatus.REQUESTED,
						realName = null,
						verifiedAt = null,
					),
				)

				get("/payments/v1/checkout") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("success", true)
					body("data.orderer.name", "김오늘의 소개")
					body("data.orderer.email", "orderer@test.com")
					body("data.orderer.phoneNumber", "01011112222")
				}
			}
		}

		context("프로필·본인인증이 없는 사용자가 조회하면") {
			it("모든 주문자 필드를 null로 반환한다") {
				val user: UserEntity = IntegrationUtil.persist(
					UserEntityFixture.create(providerId = "checkout-2", email = null),
				)

				get("/payments/v1/checkout") {
					bearer(accessTokenFor(user.id!!))
				} expect {
					status(200)
					body("success", true)
					body("data.orderer.name", nullValue())
					body("data.orderer.email", nullValue())
					body("data.orderer.phoneNumber", nullValue())
				}
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				get("/payments/v1/checkout") {} expect {
					status(401)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QIdentityVerificationEntity.identityVerificationEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}
})
```

- [ ] **Step 4: 테스트 실행해 실패(RED) 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.payments.PaymentsCheckoutE2ETest"`
Expected: FAIL — 엔드포인트가 없어 인증 케이스들이 200 대신 404(또는 401)를 받는다. 비인증 401 케이스만 통과할 수 있다. (커밋은 Task 4에서 GREEN 확인 후 일괄 수행)

---

### Task 2: core payments query 계층 (dto·dao·in-port·service)

**Files:**
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments/query/dto/OrdererView.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments/query/dto/CheckoutView.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments/query/dao/GetCheckoutOrdererDao.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments/query/service/port/in/GetCheckoutUseCase.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments/query/service/GetCheckoutService.kt`

**Interfaces:**
- Consumes: 없음 (신규 패키지)
- Produces:
  - `GetCheckoutUseCase.getCheckout(userId: Long): CheckoutView` — Task 4의 컨트롤러가 주입
  - `GetCheckoutOrdererDao.findOrdererByUserId(userId: Long): OrdererView?` — Task 3의 DaoImpl이 구현
  - `CheckoutView(orderer: OrdererView)` / `OrdererView(name: String?, email: String?, phoneNumber: String?)` + `OrdererView.empty()`

- [ ] **Step 1: read model dto 작성**

`OrdererView.kt`:

```kotlin
package com.org.oneulsogae.core.payments.query.dto

/**
 * 체크아웃 화면 주문자 정보 read model.
 * 실명은 최신 VERIFIED 본인인증, 이메일은 users, 휴대폰은 user_details에서 읽는다.
 * 본인인증·프로필 미완료 사용자는 각 필드가 null일 수 있다. (미비가 화면 진입을 막지 않는다)
 */
data class OrdererView(
	val name: String?,
	val email: String?,
	val phoneNumber: String?,
) {
	companion object {
		/** users 행을 찾지 못한 사용자에 대한 대체값. (모든 필드 null) */
		fun empty(): OrdererView = OrdererView(name = null, email = null, phoneNumber = null)
	}
}
```

`CheckoutView.kt`:

```kotlin
package com.org.oneulsogae.core.payments.query.dto

/**
 * 체크아웃(결제) 화면 진입 시 조회 데이터 read model.
 * 모임·일정·금액은 offline 도메인 API가 제공하므로 여기에 두지 않는다. (추후 쿠폰 등 확장 지점)
 */
data class CheckoutView(
	val orderer: OrdererView,
)
```

- [ ] **Step 2: dao 아웃포트 작성**

`GetCheckoutOrdererDao.kt`:

```kotlin
package com.org.oneulsogae.core.payments.query.dao

import com.org.oneulsogae.core.payments.query.dto.OrdererView

/**
 * 체크아웃 주문자 정보 조회 dao(out-port). infra의 GetCheckoutOrdererDaoImpl이 구현한다.
 * users·user_details·최신 VERIFIED identity_verifications를 [OrdererView]로 투영한다.
 */
interface GetCheckoutOrdererDao {

	/** users 행이 없으면 null을 반환한다. (있으면 나머지 필드는 null 허용 투영) */
	fun findOrdererByUserId(userId: Long): OrdererView?
}
```

- [ ] **Step 3: in-port·서비스 작성**

`GetCheckoutUseCase.kt`:

```kotlin
package com.org.oneulsogae.core.payments.query.service.port.`in`

import com.org.oneulsogae.core.payments.query.dto.CheckoutView

/** 결제(체크아웃) 화면 진입 시 필요한 데이터 조회 유스케이스(in-port). */
interface GetCheckoutUseCase {

	fun getCheckout(userId: Long): CheckoutView
}
```

`GetCheckoutService.kt`:

```kotlin
package com.org.oneulsogae.core.payments.query.service

import com.org.oneulsogae.core.payments.query.dao.GetCheckoutOrdererDao
import com.org.oneulsogae.core.payments.query.dto.CheckoutView
import com.org.oneulsogae.core.payments.query.dto.OrdererView
import com.org.oneulsogae.core.payments.query.service.port.`in`.GetCheckoutUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 체크아웃 화면 조회 서비스. 주문자 정보를 read model로 반환한다.
 * 주문자 정보 미비(본인인증 전 등)는 화면 진입을 막을 사유가 아니므로 null 필드로 내려주고 예외를 던지지 않는다.
 */
@Service
@Transactional(readOnly = true)
class GetCheckoutService(
	private val getCheckoutOrdererDao: GetCheckoutOrdererDao,
) : GetCheckoutUseCase {

	override fun getCheckout(userId: Long): CheckoutView =
		CheckoutView(orderer = getCheckoutOrdererDao.findOrdererByUserId(userId) ?: OrdererView.empty())
}
```

- [ ] **Step 4: 컴파일 확인**

Run: `./gradlew :oneulsogae-core:compileKotlin`
Expected: BUILD SUCCESSFUL

---

### Task 3: infra QueryDSL DaoImpl

**Files:**
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/payments/query/GetCheckoutOrdererDaoImpl.kt`

**Interfaces:**
- Consumes: Task 2의 `GetCheckoutOrdererDao`·`OrdererView`, Q타입 `QUserEntity`/`QUserDetailEntity`/`QIdentityVerificationEntity`, `IdentityVerificationStatus.VERIFIED`
- Produces: `GetCheckoutOrdererDao`의 Spring 빈 구현 (Task 4의 E2E가 전 구간으로 검증)

- [ ] **Step 1: DaoImpl 작성**

`GetCheckoutOrdererDaoImpl.kt` 전체 내용:

```kotlin
package com.org.oneulsogae.infra.payments.query

import com.org.oneulsogae.core.payments.query.dao.GetCheckoutOrdererDao
import com.org.oneulsogae.core.payments.query.dto.OrdererView
import com.org.oneulsogae.core.user.command.domain.IdentityVerificationStatus
import com.org.oneulsogae.infra.user.command.entity.QIdentityVerificationEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.org.oneulsogae.infra.user.command.entity.QUserEntity
import com.querydsl.core.Tuple
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * 체크아웃 주문자 정보 조회 dao([GetCheckoutOrdererDao])의 QueryDSL 구현.
 * user 계열 엔티티를 [OrdererView]로 직접 투영한다. (표시용 조인 패턴 — user 도메인 포트를 거치지 않는다)
 * 실명(최신 VERIFIED 1건)은 상관 서브쿼리 조인 대신 별도 쿼리로 읽는다.
 * (① users PK seek ⟕ user_details ux_user_id, ② identity_verifications idx_iv_user_id + id desc — 둘 다 인덱스 seek)
 */
@Component
class GetCheckoutOrdererDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetCheckoutOrdererDao {

	override fun findOrdererByUserId(userId: Long): OrdererView? {
		val user: QUserEntity = QUserEntity.userEntity
		val userDetail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		val identity: QIdentityVerificationEntity = QIdentityVerificationEntity.identityVerificationEntity

		val base: Tuple = queryFactory
			.select(user.email, userDetail.phoneNumber)
			.from(user)
			.leftJoin(userDetail).on(userDetail.userId.eq(user.id))
			.where(user.id.eq(userId))
			.fetchOne() ?: return null

		val realName: String? = queryFactory
			.select(identity.realName)
			.from(identity)
			.where(
				identity.userId.eq(userId),
				identity.status.eq(IdentityVerificationStatus.VERIFIED),
			)
			.orderBy(identity.id.desc())
			.fetchFirst()

		return OrdererView(
			name = realName,
			email = base.get(user.email),
			phoneNumber = base.get(userDetail.phoneNumber),
		)
	}
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew :oneulsogae-infra:compileKotlin`
Expected: BUILD SUCCESSFUL (Q타입은 기존 엔티티에서 이미 생성됨)

---

### Task 4: api 컨트롤러·응답 DTO + E2E GREEN + 커밋

**Files:**
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/payments/response/OrdererResponse.kt`
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/payments/response/CheckoutResponse.kt`
- Create: `oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/payments/PaymentsController.kt`

**Interfaces:**
- Consumes: Task 2의 `GetCheckoutUseCase`·`CheckoutView`·`OrdererView`, 기존 `@LoginUser`/`AuthUser`/`ApiResponse`
- Produces: `GET /payments/v1/checkout` 엔드포인트 (Task 1의 E2E를 GREEN으로 만든다)

- [ ] **Step 1: 응답 DTO 작성**

`OrdererResponse.kt`:

```kotlin
package com.org.oneulsogae.api.payments.response

import com.org.oneulsogae.core.payments.query.dto.OrdererView

/** 체크아웃 주문자 정보 응답. 본인인증·프로필 미완료 사용자는 각 필드가 null일 수 있다. */
data class OrdererResponse(
	val name: String?,
	val email: String?,
	val phoneNumber: String?,
) {
	companion object {
		fun of(view: OrdererView): OrdererResponse =
			OrdererResponse(
				name = view.name,
				email = view.email,
				phoneNumber = view.phoneNumber,
			)
	}
}
```

`CheckoutResponse.kt`:

```kotlin
package com.org.oneulsogae.api.payments.response

import com.org.oneulsogae.core.payments.query.dto.CheckoutView

/** 체크아웃(결제) 화면 진입 시 조회 데이터 응답. (추후 쿠폰 등 확장 지점) */
data class CheckoutResponse(
	val orderer: OrdererResponse,
) {
	companion object {
		fun of(view: CheckoutView): CheckoutResponse =
			CheckoutResponse(orderer = OrdererResponse.of(view.orderer))
	}
}
```

- [ ] **Step 2: 컨트롤러 작성**

`PaymentsController.kt`:

```kotlin
package com.org.oneulsogae.api.payments

import com.org.oneulsogae.api.payments.response.CheckoutResponse
import com.org.oneulsogae.auth.AuthUser
import com.org.oneulsogae.auth.LoginUser
import com.org.oneulsogae.core.common.response.ApiResponse
import com.org.oneulsogae.core.payments.query.service.port.`in`.GetCheckoutUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "결제", description = "결제(체크아웃) 화면 데이터 조회")
@RestController
@RequestMapping("/payments/v1")
class PaymentsController(
	private val getCheckoutUseCase: GetCheckoutUseCase,
) {

	/** 결제 화면 진입 시 필요한 주문자 정보를 조회한다. (모임·일정·금액은 offline API가 제공) */
	@Operation(
		summary = "체크아웃 화면 조회",
		description = "결제 화면 진입 시 필요한 주문자 정보(실명·이메일·휴대폰)를 조회한다. 본인인증 전 사용자는 각 필드가 null일 수 있다.",
	)
	@GetMapping("/checkout")
	fun getCheckout(
		@LoginUser user: AuthUser,
	): ApiResponse<CheckoutResponse> =
		ApiResponse.success(CheckoutResponse.of(getCheckoutUseCase.getCheckout(user.id)))
}
```

- [ ] **Step 3: E2E 실행해 GREEN 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.payments.PaymentsCheckoutE2ETest"`
Expected: PASS (3개 케이스 모두)

- [ ] **Step 4: 전체 빌드로 회귀 확인**

Run: `./gradlew :oneulsogae-core:compileKotlin :oneulsogae-infra:compileKotlin :oneulsogae-api:compileTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/payments \
        oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/payments \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/payments \
        oneulsogae-infra/src/testFixtures/kotlin/com/org/oneulsogae/infra/fixture/UserDetailEntityFixture.kt \
        oneulsogae-infra/src/testFixtures/kotlin/com/org/oneulsogae/infra/fixture/IdentityVerificationEntityFixture.kt
git commit -m "feat(payments): 체크아웃 주문자 정보 조회 API 추가

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```
