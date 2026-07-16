# 체크아웃·결제완료 productId 전환 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 체크아웃 조회와 결제완료 접수가 `gatheringId+scheduleId+gender` 대신 `productId` 하나로 상품을 식별하게 한다.

**Architecture:** products 소유 도메인(gathering)의 query in-port에 `getProduct(productId) → (gatheringId, scheduleId, gender)` 해석을 추가하고, payments의 두 진입점(체크아웃 컨트롤러·결제완료 서비스)이 이를 사용한다. 프론트는 offline 모임 상세 응답의 일정 아이템에 새로 추가되는 `productId`(해당 성별 NORMAL 상품 id)를 사용한다. 체크아웃 응답 형태는 불변.

**Tech Stack:** Kotlin 2.2.21 / Spring Boot 4.0.6 / QueryDSL / Kotest / RestAssured E2E(Testcontainers)

**Spec:** `docs/superpowers/specs/2026-07-16-checkout-product-id-design.md`

## Global Constraints

- 응답·주석·커밋 메시지는 한국어. 커밋 형식 `<type>(<domain>): <설명>`(gathering·payments).
- 변수·반환 타입·람다 파라미터 타입을 생략하지 않는다. 들여쓰기는 탭.
- Controller는 in-port UseCase만 주입. 도메인 간 접근은 상대 도메인 in-port 주입.
- 신규 에러: `GATHERING_PRODUCT_NOT_FOUND`("GATHERING-006", 404, "상품을 찾을 수 없습니다."), `PAYMENT_PRODUCT_GENDER_MISMATCH`("PAYMENTS-003", 400, "본인 성별의 상품이 아닙니다.").
- productId는 타입 무관 식별자다(EARLY_BIRD/DISCOUNT 행 id도 같은 모임·일정·성별로 해석). 실결제가는 서버 티어 규칙으로 확정.
- 체크아웃 응답(orderer·product·paymentMethods) 형태 불변. 상세 응답은 `productId` 필드 **추가만**(하위호환).
- DDL 변경 없음.
- 테스트 실행: `./gradlew :meeple-api:test --tests "<FQCN>"`. E2E는 Testcontainers(로컬 Docker).

---

### Task 1: gathering query 확장 — getProduct(productId)

**Files:**
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/gathering/query/dto/GatheringProductIdentity.kt`
- Modify: `meeple-core/src/main/kotlin/com/org/meeple/core/gathering/GatheringErrorCode.kt`
- Modify: `meeple-core/src/main/kotlin/com/org/meeple/core/gathering/query/dao/GetGatheringDao.kt`
- Modify: `meeple-core/src/main/kotlin/com/org/meeple/core/gathering/query/service/port/in/GetGatheringsUseCase.kt`
- Modify: `meeple-core/src/main/kotlin/com/org/meeple/core/gathering/query/service/GetGatheringsService.kt`
- Modify: `meeple-infra/src/main/kotlin/com/org/meeple/infra/gathering/query/GetGatheringDaoImpl.kt`

**Interfaces:**
- Consumes: `QGatheringProductEntity`(기존), `BusinessException`/`ErrorCode`(기존)
- Produces:
  - `GatheringProductIdentity(productId: Long, gatheringId: Long, scheduleId: Long, gender: Gender)`
  - `GetGatheringsUseCase.getProduct(productId: Long): GatheringProductIdentity` — 없으면 404(GATHERING-006)
  - `GetGatheringDao.findProductById(productId: Long): GatheringProductIdentity?`

- [ ] **Step 1: read model·에러 코드·포트 추가**

`GatheringProductIdentity.kt`:

```kotlin
package com.org.meeple.core.gathering.query.dto

import com.org.meeple.common.user.Gender

/**
 * 상품 한 건의 식별 정보 read model. productId로 (모임, 일정, 성별)을 해석한다.
 * 체크아웃 조회·결제완료 접수가 productId 하나로 상품을 지정할 때 쓴다.
 * 타입은 식별에 쓰지 않는다(어느 티어 행이든 같은 모임·일정·성별로 해석되고, 실결제가는 서버 티어 규칙으로 확정).
 */
data class GatheringProductIdentity(
	val productId: Long,
	val gatheringId: Long,
	val scheduleId: Long,
	val gender: Gender,
)
```

`GatheringErrorCode.kt` — `GATHERING_ALREADY_JOINED` 아래에 추가:

```kotlin
	/** 상품을 id로 찾지 못함(없거나 삭제됨). */
	GATHERING_PRODUCT_NOT_FOUND("GATHERING-006", "상품을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
```

`GetGatheringDao.kt` — 인터페이스에 추가:

```kotlin
	/** 상품 한 건의 식별 정보를 id로 조회한다. 없으면 null. (soft delete 제외) */
	fun findProductById(productId: Long): GatheringProductIdentity?
```

(import에 `GatheringProductIdentity` 추가)

`GetGatheringsUseCase.kt` — 인터페이스에 추가:

```kotlin
	/** 상품 한 건의 식별 정보(모임·일정·성별)를 id로 조회한다. 없으면 404(GATHERING-006). */
	fun getProduct(productId: Long): GatheringProductIdentity
```

(import에 `GatheringProductIdentity` 추가)

- [ ] **Step 2: 서비스·DAO 구현**

`GetGatheringsService.kt` — `getGathering` 아래에 추가(import에 `GatheringProductIdentity` 추가):

```kotlin
	override fun getProduct(productId: Long): GatheringProductIdentity =
		getGatheringDao.findProductById(productId)
			?: throw BusinessException(GatheringErrorCode.GATHERING_PRODUCT_NOT_FOUND, "상품을 찾을 수 없습니다: $productId")
```

`GetGatheringDaoImpl.kt` — `findSchedulesByGatheringId` 아래에 추가:

```kotlin
	override fun findProductById(productId: Long): GatheringProductIdentity? {
		val product: QGatheringProductEntity = QGatheringProductEntity.gatheringProductEntity
		return queryFactory
			.select(
				Projections.constructor(
					GatheringProductIdentity::class.java,
					product.id,
					product.gatheringId,
					product.scheduleId,
					product.gender,
				),
			)
			.from(product)
			.where(product.id.eq(productId))
			.fetchOne()
	}
```

(import에 `GatheringProductIdentity` 추가. `Projections`·`QGatheringProductEntity`는 기존 import)

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew :meeple-core:compileKotlin :meeple-infra:compileKotlin`
Expected: BUILD SUCCESSFUL (신규 메서드는 아직 소비처 없음 — E2E 검증은 Task 3·4에서)

- [ ] **Step 4: 커밋**

```bash
git add meeple-core/src/main/kotlin/com/org/meeple/core/gathering/query/dto/GatheringProductIdentity.kt \
  meeple-core/src/main/kotlin/com/org/meeple/core/gathering/GatheringErrorCode.kt \
  meeple-core/src/main/kotlin/com/org/meeple/core/gathering/query/dao/GetGatheringDao.kt \
  meeple-core/src/main/kotlin/com/org/meeple/core/gathering/query/service/port/in/GetGatheringsUseCase.kt \
  meeple-core/src/main/kotlin/com/org/meeple/core/gathering/query/service/GetGatheringsService.kt \
  meeple-infra/src/main/kotlin/com/org/meeple/infra/gathering/query/GetGatheringDaoImpl.kt
git commit -m "feat(gathering): productId로 모임·일정·성별을 해석하는 상품 단건 조회 추가"
```

---

### Task 2: offline 상세 응답에 productId 추가 (하위호환)

**Files:**
- Modify: `meeple-core/src/main/kotlin/com/org/meeple/core/gathering/query/dto/GatheringProductView.kt`
- Modify: `meeple-core/src/main/kotlin/com/org/meeple/core/gathering/query/dto/GatheringScheduleView.kt`
- Modify: `meeple-infra/src/main/kotlin/com/org/meeple/infra/gathering/query/GetGatheringDaoImpl.kt`
- Modify: `meeple-api/src/main/kotlin/com/org/meeple/api/offline/response/GatheringDetailResponse.kt`
- Test(Create): `meeple-api/src/test/kotlin/com/org/meeple/domain/gathering/GatheringScheduleViewTest.kt`
- Test(Modify): `meeple-api/src/test/kotlin/com/org/meeple/api/offline/OfflineGatheringDetailE2ETest.kt`

**Interfaces:**
- Consumes: 기존 `GatheringScheduleView(id, startAt, endAt, maleRemaining, femaleRemaining, earlyBirdCapacity, earlyBirdRemaining, status, products)`
- Produces:
  - `GatheringProductView(id: Long, gender, type, price)` — `id` 필드가 맨 앞에 추가됨
  - `GatheringScheduleView.productIdFor(gender: Gender): Long` — 해당 성별 NORMAL 상품 id, 부재 시 checkNotNull 실패
  - `GatheringDetailResponse.Schedule.productId: Long` (응답 필드 추가)

- [ ] **Step 1: 유닛 테스트 작성 (실패 확인)**

`meeple-api/src/test/kotlin/com/org/meeple/domain/gathering/GatheringScheduleViewTest.kt`:

```kotlin
package com.org.meeple.domain.gathering

import com.org.meeple.common.gathering.GatheringProductType
import com.org.meeple.common.gathering.GatheringScheduleStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.core.gathering.query.dto.GatheringProductView
import com.org.meeple.core.gathering.query.dto.GatheringScheduleView
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

class GatheringScheduleViewTest : DescribeSpec({

	fun view(products: List<GatheringProductView>): GatheringScheduleView =
		GatheringScheduleView(
			id = 1L,
			startAt = LocalDateTime.of(2999, 1, 1, 19, 0, 0),
			endAt = null,
			maleRemaining = 4,
			femaleRemaining = 4,
			earlyBirdCapacity = null,
			earlyBirdRemaining = null,
			status = GatheringScheduleStatus.SCHEDULED,
			products = products,
		)

	describe("productIdFor") {

		it("해당 성별 NORMAL 상품의 id를 돌려준다") {
			val target: GatheringScheduleView = view(
				listOf(
					GatheringProductView(id = 11L, gender = Gender.MALE, type = GatheringProductType.NORMAL, price = 10000),
					GatheringProductView(id = 12L, gender = Gender.FEMALE, type = GatheringProductType.NORMAL, price = 8000),
					GatheringProductView(id = 13L, gender = Gender.MALE, type = GatheringProductType.EARLY_BIRD, price = 7000),
				),
			)

			target.productIdFor(Gender.MALE) shouldBe 11L
			target.productIdFor(Gender.FEMALE) shouldBe 12L
		}

		it("해당 성별 NORMAL 상품이 없으면 실패한다") {
			val target: GatheringScheduleView = view(
				listOf(
					GatheringProductView(id = 11L, gender = Gender.MALE, type = GatheringProductType.NORMAL, price = 10000),
				),
			)

			shouldThrow<IllegalStateException> { target.productIdFor(Gender.FEMALE) }
		}
	}
})
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.domain.gathering.GatheringScheduleViewTest"`
Expected: 컴파일 실패 (`GatheringProductView`에 `id` 파라미터 없음, `productIdFor` unresolved)

- [ ] **Step 3: 구현**

`GatheringProductView.kt` 전체 교체:

```kotlin
package com.org.meeple.core.gathering.query.dto

import com.org.meeple.common.gathering.GatheringProductType
import com.org.meeple.common.user.Gender

/** 일정의 성별·티어별 가격 상품 한 건(read model). 금액은 저장된 확정가다. [id]는 체크아웃·결제완료의 상품 식별자다. */
data class GatheringProductView(
	val id: Long,
	val gender: Gender,
	val type: GatheringProductType,
	val price: Int,
)
```

`GatheringScheduleView.kt` — `salePriceFor` 아래·`priceFor` 위에 추가:

```kotlin
	/** [gender] 성별의 정가(NORMAL) 상품 id. 프론트가 체크아웃·결제완료에 넘길 상품 식별자다. */
	fun productIdFor(gender: Gender): Long =
		checkNotNull(
			products.firstOrNull { product: GatheringProductView ->
				product.gender == gender && product.type == GatheringProductType.NORMAL
			}?.id,
		) { "정가 상품이 없습니다: $id" }
```

`GetGatheringDaoImpl.kt`의 `findSchedulesByGatheringId` 안 상품 투영 람다에 id 추가:

```kotlin
				{ row: GatheringProductEntity -> GatheringProductView(id = checkNotNull(row.id), gender = row.gender, type = row.type, price = row.price) },
```

`GatheringDetailResponse.kt`의 `Schedule` — `genderDescription` 다음에 필드 추가:

```kotlin
		val productId: Long,
```

`Schedule.of`의 생성 인자에 추가(`genderDescription = gender.description,` 다음):

```kotlin
					productId = view.productIdFor(gender),
```

`Schedule` KDoc에 한 줄 추가: `[productId]는 이 성별의 정가(NORMAL) 상품 id로, 체크아웃·결제완료 요청에 그대로 쓴다.`

- [ ] **Step 4: 유닛 테스트 통과 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.domain.gathering.GatheringScheduleViewTest"`
Expected: PASS

- [ ] **Step 5: E2E 단언 추가**

`OfflineGatheringDetailE2ETest.kt` — 가격을 검증하는 첫 번째 상세 조회 컨텍스트에서:

1. 해당 일정의 `tierSet` persist 부분을 persist 결과를 캡처하도록 변경:

```kotlin
			val products: List<GatheringProductEntity> = GatheringProductEntityFixture.tierSet(
				/* 기존 인자 그대로 */
			).map { product: GatheringProductEntity -> IntegrationUtil.persist(product) }
			val maleProductId: Long = products.first { product: GatheringProductEntity ->
				product.gender == Gender.MALE && product.type == GatheringProductType.NORMAL
			}.id!!
```

2. 응답 단언에 추가(남성 아이템 검증 부분):

```kotlin
					body("data.schedules[0].productId", maleProductId.toInt())
```

(해당 파일의 기존 단언 스타일에 맞춰 인덱스·성별을 조정한다. `Gender`·`GatheringProductType` import 추가.)

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.offline.OfflineGatheringDetailE2ETest"`
Expected: PASS

- [ ] **Step 6: 커밋**

```bash
git add meeple-core/src/main/kotlin/com/org/meeple/core/gathering/query/dto/GatheringProductView.kt \
  meeple-core/src/main/kotlin/com/org/meeple/core/gathering/query/dto/GatheringScheduleView.kt \
  meeple-infra/src/main/kotlin/com/org/meeple/infra/gathering/query/GetGatheringDaoImpl.kt \
  meeple-api/src/main/kotlin/com/org/meeple/api/offline/response/GatheringDetailResponse.kt \
  meeple-api/src/test/kotlin/com/org/meeple/domain/gathering/GatheringScheduleViewTest.kt \
  meeple-api/src/test/kotlin/com/org/meeple/api/offline/OfflineGatheringDetailE2ETest.kt
git commit -m "feat(gathering): 모임 상세 일정 아이템에 상품 id(productId) 추가"
```

---

### Task 3: 체크아웃 API productId 전환 (breaking)

**Files:**
- Modify: `meeple-api/src/main/kotlin/com/org/meeple/api/payments/PaymentsController.kt`
- Test(Modify): `meeple-api/src/test/kotlin/com/org/meeple/api/payments/PaymentsCheckoutE2ETest.kt`

**Interfaces:**
- Consumes: Task 1 `GetGatheringsUseCase.getProduct(productId): GatheringProductIdentity`
- Produces: `GET /payments/v1/checkout?productId={id}` — 응답 형태 불변

- [ ] **Step 1: 컨트롤러 변경**

`PaymentsController.kt`의 `getCheckout`을 다음으로 교체:

```kotlin
	/**
	 * 결제 화면 진입 시 필요한 주문자 정보·상품(모임 일정) 정보·활성 결제수단을 조회한다.
	 * 상품은 productId 하나로 지정하고, gathering 도메인 in-port가 (모임, 일정, 성별)로 해석한다.
	 * 매진은 soldOut 플래그로 내려주고 차단하지 않는다.
	 */
	@Operation(
		summary = "체크아웃 화면 조회",
		description = "결제 화면 진입 시 필요한 주문자 정보(실명·이메일·휴대폰), 상품 정보(모임·일정·정가·실결제가·매진 여부), 활성 결제수단 목록을 조회한다. " +
			"상품은 productId로 지정한다(모임 상세 응답의 schedules[].productId). 본인인증 전 사용자는 주문자 필드가 null일 수 있다. " +
			"상품 없음 404(GATHERING-006), 모임 없음/모집중 아님 404(GATHERING-001), 일정 미매칭 404(PAYMENTS-001).",
	)
	@GetMapping("/checkout")
	fun getCheckout(
		@LoginUser user: AuthUser,
		@RequestParam productId: Long,
	): ApiResponse<CheckoutResponse> {
		val checkout: CheckoutView = getCheckoutUseCase.getCheckout(user.id)
		val product: GatheringProductIdentity = getGatheringsUseCase.getProduct(productId)
		val gathering: GatheringDetailView = getGatheringsUseCase.getGathering(product.gatheringId)
		val schedule: GatheringScheduleView = gathering.scheduleOrNull(product.scheduleId)
			?: throw BusinessException(PaymentsErrorCode.CHECKOUT_PRODUCT_NOT_FOUND)
		return ApiResponse.success(CheckoutResponse.of(checkout, gathering, schedule, product.gender))
	}
```

import 변경: `com.org.meeple.core.gathering.query.dto.GatheringProductIdentity` 추가, `com.org.meeple.common.user.Gender` 제거(컨트롤러에서 더 이상 사용 안 함 — complete 쪽도 확인 후 미사용이면 제거).

- [ ] **Step 2: E2E 전환**

`PaymentsCheckoutE2ETest.kt`:

1. `persistGatheringWithSchedule` 헬퍼가 남성 NORMAL 상품 id까지 돌려주도록 변경:

```kotlin
	// 모집중 모임 + 일정 + 상품을 저장하고 (gatheringId, scheduleId, 남성 NORMAL productId)를 돌려준다.
	fun persistGatheringWithSchedule(
		earlyBirdDiscountRate: Int? = null,
		earlyBirdCapacity: Int? = null,
		earlyBirdRemaining: Int? = earlyBirdCapacity,
		discountMaleFee: Int? = null,
		maleRemaining: Int = 4,
	): Triple<Long, Long, Long> {
		/* gathering·schedule persist 기존 그대로 */
		val products: List<GatheringProductEntity> = GatheringProductEntityFixture.tierSet(
			gatheringId = gatheringId,
			scheduleId = scheduleId,
			maleFee = 10000,
			femaleFee = 8000,
			earlyBirdDiscountRate = earlyBirdDiscountRate,
			discountMaleFee = discountMaleFee,
		).map { product: GatheringProductEntity -> IntegrationUtil.persist(product) }
		val productId: Long = products.first { product: GatheringProductEntity ->
			product.gender == Gender.MALE && product.type == GatheringProductType.NORMAL
		}.id!!
		return Triple(gatheringId, scheduleId, productId)
	}
```

2. 모든 호출부: `val (gatheringId: Long, scheduleId: Long) = ...` → `val (gatheringId: Long, scheduleId: Long, productId: Long) = ...` (미사용 변수는 이름 유지 가능), 요청 URL을 `get("/payments/v1/checkout?productId=$productId")`로 교체. `gender=FEMALE`로 조회하던 케이스가 있으면 여성 NORMAL 상품 id를 골라 쓰도록 같은 패턴으로 적응한다.

3. 404 케이스 교체·추가:
- 기존 "없는 일정/모임" 류 케이스를 productId 의미에 맞게 재구성:

```kotlin
		context("없는 productId로 조회하면") {
			it("404 GATHERING-006을 반환한다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "checkout-404")).id!!

				get("/payments/v1/checkout?productId=999999") {
					bearer(accessTokenFor(userId))
				} expect {
					status(404)
					body("error.code", "GATHERING-006")
				}
			}
		}
```

- 기존 "모집중 아님 404(GATHERING-001)" 케이스는 모집중 아닌 모임의 상품 id로 요청하도록 적응한다(모임 status만 다르게 persist).
- 기존 PAYMENTS-001 케이스는 데이터 정합 방어 검증으로 재구성: 존재하는 모임 A의 gatheringId + **다른 모임 B의 scheduleId**를 가진 상품 행을 `GatheringProductEntityFixture.create(gatheringId = aId, scheduleId = bScheduleId)`로 persist하고 그 id로 요청 → 404 PAYMENTS-001.

4. import에 `GatheringProductType`·`Gender`(common.user) 추가. 파일 상단 KDoc의 URL 표기도 `?productId=`로 갱신.

- [ ] **Step 3: E2E 통과 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.payments.PaymentsCheckoutE2ETest"`
Expected: PASS

- [ ] **Step 4: 커밋**

```bash
git add meeple-api/src/main/kotlin/com/org/meeple/api/payments/PaymentsController.kt \
  meeple-api/src/test/kotlin/com/org/meeple/api/payments/PaymentsCheckoutE2ETest.kt
git commit -m "feat(payments): 체크아웃 조회를 productId 단일 파라미터로 전환"
```

---

### Task 4: 결제완료 API productId 전환 (breaking)

**Files:**
- Modify: `meeple-core/src/main/kotlin/com/org/meeple/core/payments/PaymentsErrorCode.kt`
- Modify: `meeple-core/src/main/kotlin/com/org/meeple/core/payments/command/application/port/in/command/CompletePaymentCommand.kt`
- Modify: `meeple-core/src/main/kotlin/com/org/meeple/core/payments/command/application/CompletePaymentService.kt`
- Modify: `meeple-api/src/main/kotlin/com/org/meeple/api/payments/request/CompletePaymentRequest.kt`
- Modify: `meeple-api/src/main/kotlin/com/org/meeple/api/payments/PaymentsController.kt` (@Operation description만)
- Test(Modify): `meeple-api/src/test/kotlin/com/org/meeple/api/payments/PaymentsCompleteE2ETest.kt`

**Interfaces:**
- Consumes: Task 1 `GetGatheringsUseCase.getProduct(productId): GatheringProductIdentity`
- Produces: `POST /payments/v1/complete` body `{"productId": <id>}`, `CompletePaymentCommand(productId: Long)`, `PAYMENT_PRODUCT_GENDER_MISMATCH`("PAYMENTS-003", 400)

- [ ] **Step 1: 에러 코드·커맨드·요청 변경**

`PaymentsErrorCode.kt` — `ORDERER_GENDER_REQUIRED` 아래에 추가:

```kotlin
	/** 결제완료 접수의 productId가 본인 프로필 성별의 상품이 아님. */
	PAYMENT_PRODUCT_GENDER_MISMATCH("PAYMENTS-003", "본인 성별의 상품이 아닙니다.", HttpStatus.BAD_REQUEST),
```

`CompletePaymentCommand.kt` 전체 교체:

```kotlin
package com.org.meeple.core.payments.command.application.port.`in`.command

/** 결제완료 접수 명령. 상품은 productId 하나로 지정한다. 성별은 받지 않는다 — 본인 프로필 성별을 서버가 강제한다. */
data class CompletePaymentCommand(
	val productId: Long,
)
```

`CompletePaymentRequest.kt` 전체 교체:

```kotlin
package com.org.meeple.api.payments.request

import com.org.meeple.core.payments.command.application.port.`in`.command.CompletePaymentCommand
import jakarta.validation.constraints.NotNull

/** 결제완료 접수 요청. 상품은 productId(모임 상세 응답의 schedules[].productId)로 지정한다. 성별은 받지 않는다(본인 프로필 성별을 서버가 강제). */
data class CompletePaymentRequest(
	@field:NotNull
	val productId: Long?,
) {

	fun toCommand(): CompletePaymentCommand =
		CompletePaymentCommand(productId = productId!!)
}
```

- [ ] **Step 2: 서비스 변경**

`CompletePaymentService.kt` 전체 교체:

```kotlin
package com.org.meeple.core.payments.command.application

import com.org.meeple.common.user.Gender
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.gathering.command.application.port.`in`.RegisterGatheringMemberUseCase
import com.org.meeple.core.gathering.command.application.port.`in`.command.RegisterGatheringMemberCommand
import com.org.meeple.core.gathering.command.application.port.`in`.result.RegisterGatheringMemberResult
import com.org.meeple.core.gathering.query.dto.GatheringProductIdentity
import com.org.meeple.core.gathering.query.service.port.`in`.GetGatheringsUseCase
import com.org.meeple.core.payments.PaymentsErrorCode
import com.org.meeple.core.payments.command.application.port.`in`.CompletePaymentUseCase
import com.org.meeple.core.payments.command.application.port.`in`.command.CompletePaymentCommand
import com.org.meeple.core.payments.command.application.port.`in`.result.CompletePaymentResult
import com.org.meeple.core.payments.command.application.port.out.SavePaymentPort
import com.org.meeple.core.payments.command.domain.Payment
import com.org.meeple.core.user.query.service.port.`in`.GetUserDetailUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [CompletePaymentUseCase] 구현.
 * 본인 프로필 성별을 확정하고(요청으로 받지 않음), productId를 gathering in-port로 (모임, 일정, 성별)로 해석한다.
 * 상품 성별이 프로필 성별과 다르면 400(PAYMENTS-003) — 체크아웃에서 본 가격과 접수 가격이 달라지는 혼란을 막는다.
 * gathering in-port로 참가를 승인대기 등록한 뒤 서버 확정가로 결제 기록을 남긴다.
 * 참가 등록과 결제 기록은 같은 트랜잭션이다(둘 중 하나만 남지 않는다).
 */
@Service
@Transactional
class CompletePaymentService(
	private val getUserDetailUseCase: GetUserDetailUseCase,
	private val getGatheringsUseCase: GetGatheringsUseCase,
	private val registerGatheringMemberUseCase: RegisterGatheringMemberUseCase,
	private val savePaymentPort: SavePaymentPort,
) : CompletePaymentUseCase {

	override fun complete(userId: Long, command: CompletePaymentCommand): CompletePaymentResult {
		val gender: Gender = getUserDetailUseCase.getByUserId(userId).gender
			?: throw BusinessException(PaymentsErrorCode.ORDERER_GENDER_REQUIRED)

		val product: GatheringProductIdentity = getGatheringsUseCase.getProduct(command.productId)
		if (product.gender != gender) {
			throw BusinessException(
				PaymentsErrorCode.PAYMENT_PRODUCT_GENDER_MISMATCH,
				"본인 성별의 상품이 아닙니다: ${command.productId}",
			)
		}

		val registered: RegisterGatheringMemberResult = registerGatheringMemberUseCase.register(
			RegisterGatheringMemberCommand(
				gatheringId = product.gatheringId,
				scheduleId = product.scheduleId,
				userId = userId,
				gender = gender,
			),
		)

		savePaymentPort.save(
			Payment(
				userId = userId,
				gatheringId = product.gatheringId,
				scheduleId = product.scheduleId,
				gender = gender,
				amount = registered.amount,
			),
		)
		return CompletePaymentResult(amount = registered.amount)
	}
}
```

`PaymentsController.kt`의 `complete` @Operation description을 다음으로 교체:

```kotlin
	@Operation(
		summary = "결제완료 접수",
		description = "결제 완료를 접수해 참가를 승인대기로 등록한다. 상품은 productId로 지정한다(모임 상세 응답의 schedules[].productId). " +
			"성별 여분·얼리버드 여분을 접수 시점에 차감한다. " +
			"상품 없음 404(GATHERING-006), 타성별 상품 400(PAYMENTS-003), 판매 중 아님 409(GATHERING-003), " +
			"매진 409(GATHERING-004), 중복 접수 409(GATHERING-005), 성별 미확정 400(PAYMENTS-002).",
	)
```

- [ ] **Step 3: E2E 전환**

`PaymentsCompleteE2ETest.kt`:

1. `persistGatheringWithSchedule` 헬퍼를 Task 3과 같은 패턴으로 `Triple<Long, Long, Long>`(gatheringId, scheduleId, 남성 NORMAL productId) 반환으로 변경(tierSet persist를 `.map { IntegrationUtil.persist(it) }`로 캡처).
2. 모든 요청 body를 `"""{"productId": $productId}"""`로 교체(구조분해에 productId 추가). scheduleId·gatheringId는 검증용(findMember/findSchedule)으로 계속 사용.
3. 기존 "일정 없음 404(GATHERING-002)" 케이스는 "없는 productId 404(GATHERING-006)" 케이스로 교체:

```kotlin
		context("없는 productId로 결제완료하면") {
			it("404 GATHERING-006을 반환한다") {
				val userId: Long = persistUserWithGender(providerId = "pay-complete-nf")

				post("/payments/v1/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"productId": 999999}""")
				} expect {
					status(404)
					body("error.code", "GATHERING-006")
				}
			}
		}
```

4. 성별 불일치 케이스 추가:

```kotlin
		context("타성별 상품의 productId로 결제완료하면") {
			it("400 PAYMENTS-003을 반환하고 아무것도 저장하지 않는다") {
				val userId: Long = persistUserWithGender(providerId = "pay-complete-gm", gender = Gender.FEMALE)
				val (gatheringId: Long, scheduleId: Long, maleProductId: Long) = persistGatheringWithSchedule()

				post("/payments/v1/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"productId": $maleProductId}""")
				} expect {
					status(400)
					body("error.code", "PAYMENTS-003")
				}

				findMember(scheduleId, userId) shouldBe null
			}
		}
```

5. body에 productId가 없는(빈 body) 400 케이스가 기존에 있으면 그대로 유효 — 없으면 추가하지 않는다(기존 검증 범위 유지). 파일 상단 KDoc의 에러 나열을 새 의미(GATHERING-006·PAYMENTS-003 포함)로 갱신.

- [ ] **Step 4: E2E 통과 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.payments.PaymentsCompleteE2ETest"`
Expected: PASS

- [ ] **Step 5: 전체 회귀 확인**

Run: `./gradlew :meeple-api:test`
Expected: PASS (체크아웃·offline·어드민 포함 전체)

- [ ] **Step 6: 커밋**

```bash
git add meeple-core/src/main/kotlin/com/org/meeple/core/payments/PaymentsErrorCode.kt \
  meeple-core/src/main/kotlin/com/org/meeple/core/payments/command/application/port/in/command/CompletePaymentCommand.kt \
  meeple-core/src/main/kotlin/com/org/meeple/core/payments/command/application/CompletePaymentService.kt \
  meeple-api/src/main/kotlin/com/org/meeple/api/payments/request/CompletePaymentRequest.kt \
  meeple-api/src/main/kotlin/com/org/meeple/api/payments/PaymentsController.kt \
  meeple-api/src/test/kotlin/com/org/meeple/api/payments/PaymentsCompleteE2ETest.kt
git commit -m "feat(payments): 결제완료 접수를 productId 기반으로 전환하고 타성별 상품 거부"
```

---

## 완료 후 사용자 안내 사항 (코드 외)

- **프론트엔드 변경**(직접 수정하지 않음):
  - 모임 상세: `schedules[]` 아이템에 `productId` 추가(하위호환 — 먼저 배포해도 안전).
  - 체크아웃: 쿼리 파라미터 `gatheringId&scheduleId&gender` → `productId` (**breaking**).
  - 결제완료: body `{gatheringId, scheduleId}` → `{productId}` (**breaking**).
  - breaking 2건은 프론트 반영과 배포 타이밍을 맞춰야 한다.
- DDL 변경 없음.
