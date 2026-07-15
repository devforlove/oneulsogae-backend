# payments 체크아웃 확장(상품·결제수단) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `GET /payments/v1/checkout`이 주문자 정보에 더해 상품(모임 일정) 정보와 결제수단 목록을 반환하도록 확장한다.

**Architecture:** 결제수단은 `payment_methods` 참조 테이블(읽기 전용, image_template 패턴)을 payments 도메인 dao로 조회한다. 상품 정보는 `PaymentsController`가 gathering in-port(`GetGatheringsUseCase`)를 조합해 얻는다. 금액 티어 계산은 `GatheringScheduleView`의 메서드로 캡슐화해 offline 응답과 공유한다.

**Tech Stack:** Kotlin 2.2.21 / Spring Boot 4.0.6 / QueryDSL / Kotest + Testcontainers + RestAssured DSL

**스펙:** `docs/superpowers/specs/2026-07-15-payments-checkout-expansion-design.md`

## Global Constraints

- `salePrice` 규칙: 얼리버드 티어 존재+미소진 → `정가 × (100 - 할인율) / 100`(정수 버림) / 얼리버드 소진 & 할인가 존재 → 할인가 / 그 외 → 정가.
- `soldOut` = 해당 성별 잔여 ≤ 0. 매진이어도 200. 없는 scheduleId만 404 `PAYMENTS-001`, 모임 없음/모집중 아님은 기존 `GATHERING-001` 전파.
- `paymentMethods`: `active = true`만 `displayOrder asc, id asc` 순. code는 String(enum 만들지 않음).
- offline 상세 응답의 기존 동작은 불변이어야 한다(리팩토링 후 `OfflineGatheringDetailE2ETest` 전 케이스 GREEN).
- 코드 스타일: 탭 들여쓰기, trailing comma, 변수·반환 타입·람다 파라미터 타입 명시.
- 커밋 2회 분리: ① Task 2에서 `refactor(gathering): …`(gathering 파일만) ② Task 4에서 `feat(payments): …`(나머지 전부). 커밋 메시지 끝에 `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>` 트레일러.

---

### Task 1: PaymentMethod 엔티티·픽스처 + E2E 재작성 (RED)

**Files:**
- Create: `meeple-infra/src/main/kotlin/com/org/meeple/infra/payments/command/entity/PaymentMethodEntity.kt`
- Create: `meeple-infra/src/testFixtures/kotlin/com/org/meeple/infra/fixture/PaymentMethodEntityFixture.kt`
- Modify(전체 재작성): `meeple-api/src/test/kotlin/com/org/meeple/api/payments/PaymentsCheckoutE2ETest.kt`

**Interfaces:**
- Consumes: `BaseEntity`, 기존 픽스처(`GatheringEntityFixture`/`GatheringScheduleEntityFixture`/`UserEntityFixture`/`UserDetailEntityFixture`/`IdentityVerificationEntityFixture`), presigned 페이크(`https://presigned.test/<imageKey>`)
- Produces: `PaymentMethodEntity(code, name, displayOrder, active)` — Task 3의 DaoImpl과 Task 4의 E2E GREEN이 사용. RED 상태의 E2E.

- [ ] **Step 1: PaymentMethodEntity 작성**

`meeple-infra/src/main/kotlin/com/org/meeple/infra/payments/command/entity/PaymentMethodEntity.kt` 전체 내용:

```kotlin
package com.org.meeple.infra.payments.command.entity

import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLRestriction

/**
 * payment_methods 테이블 영속성 엔티티. 체크아웃 화면에 노출할 결제수단을 [code]로 식별하는 참조 데이터다.
 * 활성 여부([active])·노출 순서([displayOrder])를 배포 없이 DB에서 조정한다. (앱은 읽기만, 행은 DB에서 관리)
 * 서버는 code에 로직을 두지 않으므로 enum 없이 문자열로 유지한다. 도메인 로직을 두지 않고 상태만 보관한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "payment_methods",
	uniqueConstraints = [
		UniqueConstraint(name = "ux_payment_method_code", columnNames = ["code"]),
	],
)
class PaymentMethodEntity(
	/** 프론트가 참조하는 고유 코드. 예: "BANK_TRANSFER". */
	@Column(name = "code", nullable = false, length = 50)
	var code: String,

	/** 표시명. 예: "무통장입금". */
	@Column(name = "name", nullable = false, length = 100)
	var name: String,

	/** 노출 순서(오름차순). */
	@Column(name = "display_order", nullable = false)
	var displayOrder: Int,

	/** 활성 여부. 비활성 수단은 응답에서 제외된다. */
	@Column(name = "active", nullable = false)
	var active: Boolean,
) : BaseEntity()
```

- [ ] **Step 2: PaymentMethodEntityFixture 작성**

`meeple-infra/src/testFixtures/kotlin/com/org/meeple/infra/fixture/PaymentMethodEntityFixture.kt` 전체 내용:

```kotlin
package com.org.meeple.infra.fixture

import com.org.meeple.infra.payments.command.entity.PaymentMethodEntity

/** [PaymentMethodEntity] 테스트 픽스처. 기본은 활성 무통장입금이다. */
object PaymentMethodEntityFixture {

	fun create(
		code: String = "BANK_TRANSFER",
		name: String = "무통장입금",
		displayOrder: Int = 1,
		active: Boolean = true,
	): PaymentMethodEntity =
		PaymentMethodEntity(
			code = code,
			name = name,
			displayOrder = displayOrder,
			active = active,
		)
}
```

- [ ] **Step 3: E2E 테스트 전체 재작성**

`meeple-api/src/test/kotlin/com/org/meeple/api/payments/PaymentsCheckoutE2ETest.kt`를 아래 내용으로 **전체 교체**:

```kotlin
package com.org.meeple.api.payments

import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.core.user.command.domain.IdentityVerificationStatus
import com.org.meeple.infra.fixture.GatheringEntityFixture
import com.org.meeple.infra.fixture.GatheringScheduleEntityFixture
import com.org.meeple.infra.fixture.IdentityVerificationEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.PaymentMethodEntityFixture
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.gathering.command.entity.QGatheringEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringScheduleEntity
import com.org.meeple.infra.payments.command.entity.QPaymentMethodEntity
import com.org.meeple.infra.user.command.entity.QIdentityVerificationEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.QUserEntity
import com.org.meeple.infra.user.command.entity.UserEntity
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.nullValue
import java.time.LocalDateTime

/**
 * `GET /payments/v1/checkout?gatheringId=&scheduleId=&gender=` E2E 테스트.
 *
 * 결제(체크아웃) 화면 진입 시 주문자 정보 + 상품(모임 일정) 정보 + 활성 결제수단 목록을 반환한다.
 * - 주문자: 실명(최신 VERIFIED 본인인증)·이메일(users)·휴대폰(user_details). 미비는 null 필드(에러 아님).
 * - 상품: 정가(price)와 서버 확정 실결제가(salePrice — 얼리버드 유효 시 얼리버드가, 소진 시 할인가, 그 외 정가), 매진은 soldOut 플래그(200).
 * - 결제수단: active만 displayOrder 순.
 * - 없는 일정은 404(PAYMENTS-001), 모임 없음/모집중 아님은 404(GATHERING-001).
 * (presigned URL은 TestFileStorageConfig의 페이크 — https://presigned.test/<imageKey>)
 */
class PaymentsCheckoutE2ETest : AbstractIntegrationSupport({

	// 모집중 모임 + 일정 1건을 저장하고 (gatheringId, scheduleId)를 돌려준다.
	fun persistGatheringWithSchedule(
		earlyBirdDiscountRate: Int? = null,
		earlyBirdCapacity: Int? = null,
		earlyBirdRemaining: Int? = earlyBirdCapacity,
		discountMaleFee: Int? = null,
		maleRemaining: Int = 4,
	): Pair<Long, Long> {
		val gatheringId: Long = IntegrationUtil.persist(
			GatheringEntityFixture.create(
				title = "체크아웃 모임",
				imageKey = "gatherings/checkout.png",
				region = "서울 강남구",
				status = GatheringStatus.RECRUITING,
			),
		).id!!
		val scheduleId: Long = IntegrationUtil.persist(
			GatheringScheduleEntityFixture.create(
				gatheringId = gatheringId,
				startAt = LocalDateTime.of(2999, 1, 1, 19, 0, 0),
				maleFee = 10000,
				femaleFee = 8000,
				maleRemaining = maleRemaining,
				earlyBirdDiscountRate = earlyBirdDiscountRate,
				earlyBirdCapacity = earlyBirdCapacity,
				earlyBirdRemaining = earlyBirdRemaining,
				discountMaleFee = discountMaleFee,
			),
		).id!!
		return gatheringId to scheduleId
	}

	describe("GET /payments/v1/checkout") {

		context("본인인증 유저가 얼리버드가 유효한 일정을 조회하면") {
			it("주문자·상품(얼리버드 실결제가)·활성 결제수단만 순서대로 반환한다") {
				val user: UserEntity = IntegrationUtil.persist(
					UserEntityFixture.create(providerId = "checkout-1", email = "orderer@test.com"),
				)
				val userId: Long = user.id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, phoneNumber = "01011112222"))
				// 재인증 이력: 과거 VERIFIED → 최신 VERIFIED → 진행 중(REQUESTED). 최신 VERIFIED("김미플")가 선택되어야 한다.
				IntegrationUtil.persist(IdentityVerificationEntityFixture.create(userId = userId, realName = "김과거"))
				IntegrationUtil.persist(IdentityVerificationEntityFixture.create(userId = userId, realName = "김미플"))
				IntegrationUtil.persist(
					IdentityVerificationEntityFixture.create(
						userId = userId,
						status = IdentityVerificationStatus.REQUESTED,
						realName = null,
						verifiedAt = null,
					),
				)
				// 얼리버드 30% 유효(remaining 5) → 남성 salePrice = 10000×0.7 = 7000.
				val (gatheringId: Long, scheduleId: Long) = persistGatheringWithSchedule(
					earlyBirdDiscountRate = 30,
					earlyBirdCapacity = 5,
				)
				// 활성 2건(순서 역순 저장으로 정렬 검증) + 비활성 1건(제외 검증).
				IntegrationUtil.persist(PaymentMethodEntityFixture.create(code = "KAKAO_PAY", name = "카카오페이", displayOrder = 2))
				IntegrationUtil.persist(PaymentMethodEntityFixture.create(code = "BANK_TRANSFER", name = "무통장입금", displayOrder = 1))
				IntegrationUtil.persist(PaymentMethodEntityFixture.create(code = "CARD", name = "카드", displayOrder = 3, active = false))

				get("/payments/v1/checkout?gatheringId=$gatheringId&scheduleId=$scheduleId&gender=MALE") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("success", true)
					body("data.orderer.name", "김미플")
					body("data.orderer.email", "orderer@test.com")
					body("data.orderer.phoneNumber", "01011112222")
					body("data.product.gatheringId", gatheringId.toInt())
					body("data.product.scheduleId", scheduleId.toInt())
					body("data.product.gender", "MALE")
					body("data.product.title", "체크아웃 모임")
					body("data.product.imageUrl", "https://presigned.test/gatherings/checkout.png")
					body("data.product.region", "서울 강남구")
					body("data.product.startAt", "2999-01-01T19:00:00")
					body("data.product.price", 10000)
					body("data.product.salePrice", 7000)
					body("data.product.soldOut", false)
					body("data.paymentMethods", hasSize<Any>(2))
					body("data.paymentMethods.code", contains("BANK_TRANSFER", "KAKAO_PAY"))
					body("data.paymentMethods[0].name", "무통장입금")
				}
			}
		}

		context("얼리버드가 소진된 일정을 조회하면") {
			it("실결제가는 할인가다") {
				val (gatheringId: Long, scheduleId: Long) = persistGatheringWithSchedule(
					earlyBirdDiscountRate = 30,
					earlyBirdCapacity = 5,
					earlyBirdRemaining = 0,
					discountMaleFee = 9000,
				)

				get("/payments/v1/checkout?gatheringId=$gatheringId&scheduleId=$scheduleId&gender=MALE") {
					bearer(accessTokenFor(9001L))
				} expect {
					status(200)
					body("data.product.price", 10000)
					body("data.product.salePrice", 9000)
				}
			}
		}

		context("얼리버드 티어가 없는 일정을 조회하면") {
			it("실결제가는 정가다") {
				val (gatheringId: Long, scheduleId: Long) = persistGatheringWithSchedule()

				get("/payments/v1/checkout?gatheringId=$gatheringId&scheduleId=$scheduleId&gender=MALE") {
					bearer(accessTokenFor(9002L))
				} expect {
					status(200)
					body("data.product.price", 10000)
					body("data.product.salePrice", 10000)
				}
			}
		}

		context("해당 성별 정원이 소진된 일정을 조회하면") {
			it("200과 함께 soldOut을 true로 반환한다") {
				val (gatheringId: Long, scheduleId: Long) = persistGatheringWithSchedule(maleRemaining = 0)

				get("/payments/v1/checkout?gatheringId=$gatheringId&scheduleId=$scheduleId&gender=MALE") {
					bearer(accessTokenFor(9003L))
				} expect {
					status(200)
					body("data.product.soldOut", true)
				}
			}
		}

		context("모임에 없는 scheduleId로 조회하면") {
			it("404(PAYMENTS-001)를 반환한다") {
				val (gatheringId: Long, _) = persistGatheringWithSchedule()

				get("/payments/v1/checkout?gatheringId=$gatheringId&scheduleId=999999&gender=MALE") {
					bearer(accessTokenFor(9004L))
				} expect {
					status(404)
					body("success", false)
					body("error.code", "PAYMENTS-001")
				}
			}
		}

		context("모집중이 아닌 모임으로 조회하면") {
			it("404(GATHERING-001)를 반환한다") {
				val gatheringId: Long = IntegrationUtil.persist(
					GatheringEntityFixture.create(title = "취소된 모임", status = GatheringStatus.CANCELED),
				).id!!

				get("/payments/v1/checkout?gatheringId=$gatheringId&scheduleId=1&gender=MALE") {
					bearer(accessTokenFor(9005L))
				} expect {
					status(404)
					body("success", false)
					body("error.code", "GATHERING-001")
				}
			}
		}

		context("프로필·본인인증이 없는 사용자가 조회하면") {
			it("주문자 필드는 null이고 상품·결제수단은 정상 반환한다") {
				val user: UserEntity = IntegrationUtil.persist(
					UserEntityFixture.create(providerId = "checkout-2", email = null),
				)
				val (gatheringId: Long, scheduleId: Long) = persistGatheringWithSchedule()
				IntegrationUtil.persist(PaymentMethodEntityFixture.create())

				get("/payments/v1/checkout?gatheringId=$gatheringId&scheduleId=$scheduleId&gender=MALE") {
					bearer(accessTokenFor(user.id!!))
				} expect {
					status(200)
					body("data.orderer.name", nullValue())
					body("data.orderer.email", nullValue())
					body("data.orderer.phoneNumber", nullValue())
					body("data.product.price", 10000)
					body("data.paymentMethods", hasSize<Any>(1))
				}
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				get("/payments/v1/checkout?gatheringId=1&scheduleId=1&gender=MALE") {} expect {
					status(401)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QPaymentMethodEntity.paymentMethodEntity)
		IntegrationUtil.deleteAll(QGatheringScheduleEntity.gatheringScheduleEntity)
		IntegrationUtil.deleteAll(QGatheringEntity.gatheringEntity)
		IntegrationUtil.deleteAll(QIdentityVerificationEntity.identityVerificationEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}
})
```

- [ ] **Step 4: 컴파일·RED 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.payments.PaymentsCheckoutE2ETest"`
Expected: 컴파일 성공(Q타입 `QPaymentMethodEntity`는 엔티티 추가로 생성됨), 테스트는 FAIL — 기존 엔드포인트가 `product`/`paymentMethods` 필드를 반환하지 않아 인증 케이스들이 실패한다(401 케이스만 통과 가능). **커밋하지 않는다** (Task 2는 gathering 파일만, 나머지는 Task 4에서 커밋).

---

### Task 2: gathering 금액 티어 캡슐화 리팩토링 + offline 회귀 + refactor 커밋

**Files:**
- Modify: `meeple-core/src/main/kotlin/com/org/meeple/core/gathering/query/dto/GatheringScheduleView.kt`
- Modify: `meeple-core/src/main/kotlin/com/org/meeple/core/gathering/query/dto/GatheringDetailView.kt`
- Modify: `meeple-api/src/main/kotlin/com/org/meeple/api/offline/response/GatheringDetailResponse.kt`

**Interfaces:**
- Consumes: 기존 `GatheringScheduleView` 필드, `Gender`(meeple-common)
- Produces (Task 4의 checkout 응답이 사용):
  - `GatheringScheduleView.feeFor(gender: Gender): Int`
  - `GatheringScheduleView.earlyBirdFeeFor(gender: Gender): Int?`
  - `GatheringScheduleView.discountFeeFor(gender: Gender): Int?`
  - `GatheringScheduleView.salePriceFor(gender: Gender): Int`
  - `GatheringScheduleView.soldOutFor(gender: Gender): Boolean`
  - `GatheringScheduleView.earlyBirdSoldOut: Boolean`
  - `GatheringDetailView.scheduleOrNull(scheduleId: Long): GatheringScheduleView?`

- [ ] **Step 1: GatheringScheduleView에 금액 티어 메서드 추가**

`GatheringScheduleView.kt`의 data class 본문에 메서드를 추가한다(필드 선언은 그대로). 클래스 전체가 아래 형태가 되도록 수정:

```kotlin
package com.org.meeple.core.gathering.query.dto

import com.org.meeple.common.gathering.GatheringScheduleStatus
import com.org.meeple.common.user.Gender
import java.time.LocalDateTime

/**
 * 유저용 모임 상세에 포함되는 일정 한 건(read model). 한 모임의 일정 목록으로 노출된다.
 * 시간 범위는 [startAt](필수)·[endAt](선택)으로, 진행 상태는 [status]로 표현한다.
 * 참가비는 일정별로 가진다: 정상가([maleFee]·[femaleFee], 필수), 얼리버드(할인율[earlyBirdDiscountRate]·[earlyBirdCapacity]·남은 개수[earlyBirdRemaining], 선택),
 * 할인가([discountMaleFee]·[discountFemaleFee], 선택). 없는 티어는 null.
 * 얼리버드 금액은 저장하지 않고 할인율(%)만 가지며, [earlyBirdFeeFor]가 정상가에 곱해 계산한다.
 * 남/녀 여분([maleRemaining]·[femaleRemaining])은 해당 성별 소진 판정([soldOutFor])에 쓴다.
 * 금액 티어 계산은 offline 상세 응답과 결제 체크아웃 응답이 공유하므로 이 read model에 캡슐화한다.
 */
data class GatheringScheduleView(
	val id: Long,
	val startAt: LocalDateTime,
	val endAt: LocalDateTime?,
	val maleFee: Int,
	val femaleFee: Int,
	val maleRemaining: Int,
	val femaleRemaining: Int,
	val earlyBirdDiscountRate: Int?,
	val earlyBirdCapacity: Int?,
	val earlyBirdRemaining: Int?,
	val discountMaleFee: Int?,
	val discountFemaleFee: Int?,
	val status: GatheringScheduleStatus,
) {

	/** 얼리버드 소진 여부. 티어가 있고([earlyBirdRemaining] non-null) 남은 개수가 0 이하일 때만 true. */
	val earlyBirdSoldOut: Boolean
		get() = earlyBirdRemaining != null && earlyBirdRemaining <= 0

	/** [gender] 성별의 정가. */
	fun feeFor(gender: Gender): Int =
		if (gender == Gender.MALE) maleFee else femaleFee

	/** [gender] 성별의 할인가(얼리버드 소진 시 적용 대상). 없으면 null. */
	fun discountFeeFor(gender: Gender): Int? =
		if (gender == Gender.MALE) discountMaleFee else discountFemaleFee

	/** [gender] 성별 정원 소진 여부. */
	fun soldOutFor(gender: Gender): Boolean =
		(if (gender == Gender.MALE) maleRemaining else femaleRemaining) <= 0

	/**
	 * [gender] 성별의 얼리버드가. 얼리버드 티어가 존재하고 미소진일 때만 정상가 × (100 - 할인율) / 100(버림)을 반환한다.
	 * 티어가 없거나([earlyBirdRemaining] null) 소진이면 null.
	 */
	fun earlyBirdFeeFor(gender: Gender): Int? {
		if (earlyBirdRemaining == null || earlyBirdSoldOut) return null
		return earlyBirdDiscountRate?.let { rate: Int -> feeFor(gender) * (100 - rate) / 100 }
	}

	/** [gender] 성별의 서버 확정 실결제가: 얼리버드 유효 → 얼리버드가, 소진 & 할인가 존재 → 할인가, 그 외 → 정가. */
	fun salePriceFor(gender: Gender): Int =
		earlyBirdFeeFor(gender)
			?: (if (earlyBirdSoldOut) discountFeeFor(gender) else null)
			?: feeFor(gender)
}
```

- [ ] **Step 2: GatheringDetailView에 scheduleOrNull 추가**

`GatheringDetailView.kt`의 data class 본문(보조 생성자 아래)에 메서드를 추가한다:

```kotlin
	/** [scheduleId] 일정을 찾는다. 이 모임의 일정이 아니면 null. */
	fun scheduleOrNull(scheduleId: Long): GatheringScheduleView? =
		schedules.find { schedule: GatheringScheduleView -> schedule.id == scheduleId }
```

- [ ] **Step 3: GatheringDetailResponse.Schedule.of를 메서드 사용으로 정리**

`GatheringDetailResponse.kt`에서 `Schedule`의 KDoc 세 번째 티어 줄과 `of` 본문을 아래로 교체한다(필드 선언·`GatheringDetailResponse.of`는 불변):

KDoc 교체 — 기존:
```
	 * - 얼리버드가 모두 소진되면(remaining <= 0): [fee]·[earlyBirdFee]는 null, 할인가([discountFee])만 내린다.
```
를 다음으로(실동작 정합 — fee는 항상 내려간다):
```
	 * - 얼리버드가 모두 소진되면(remaining <= 0): 정상가([fee])·할인가([discountFee])를 내리고 [earlyBirdFee]는 null.
```

`Schedule.companion.of` 전체 교체:
```kotlin
			/** [view] 일정을 [gender] 성별 아이템으로 만든다. (해당 성별의 참가비·정원 소진 여부를 반영한다. 금액 티어 계산은 [GatheringScheduleView]에 캡슐화되어 있다) */
			fun of(view: GatheringScheduleView, gender: Gender): Schedule {
				val status: GatheringScheduleItemStatus = GatheringScheduleItemStatus.of(view.status, view.soldOutFor(gender))
				return Schedule(
					scheduleId = view.id,
					gender = gender,
					genderDescription = gender.description,
					startAt = view.startAt,
					endAt = view.endAt,
					fee = view.feeFor(gender),
					earlyBirdFee = view.earlyBirdFeeFor(gender),
					discountFee = if (view.earlyBirdSoldOut) view.discountFeeFor(gender) else null,
					status = status,
					statusDescription = status.description,
				)
			}
```

- [ ] **Step 4: offline 회귀 확인 (GREEN 유지)**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.offline.OfflineGatheringDetailE2ETest"`
Expected: PASS (전 케이스 — 동작 불변 리팩토링 검증)

- [ ] **Step 5: gathering 파일만 refactor 커밋**

```bash
git add meeple-core/src/main/kotlin/com/org/meeple/core/gathering/query/dto/GatheringScheduleView.kt \
        meeple-core/src/main/kotlin/com/org/meeple/core/gathering/query/dto/GatheringDetailView.kt \
        meeple-api/src/main/kotlin/com/org/meeple/api/offline/response/GatheringDetailResponse.kt
git commit -m "refactor(gathering): 일정 금액 티어 계산을 read model 메서드로 캡슐화

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

(Task 1의 payments 변경은 워킹트리에 남는다 — Task 4에서 커밋)

---

### Task 3: payments core 확장 + 결제수단 DaoImpl

**Files:**
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/payments/PaymentsErrorCode.kt`
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/payments/query/dto/PaymentMethodView.kt`
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/payments/query/dto/PaymentMethodViews.kt`
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/payments/query/dao/GetPaymentMethodDao.kt`
- Modify: `meeple-core/src/main/kotlin/com/org/meeple/core/payments/query/dto/CheckoutView.kt`
- Modify: `meeple-core/src/main/kotlin/com/org/meeple/core/payments/query/service/GetCheckoutService.kt`
- Create: `meeple-infra/src/main/kotlin/com/org/meeple/infra/payments/query/GetPaymentMethodDaoImpl.kt`

**Interfaces:**
- Consumes: Task 1의 `PaymentMethodEntity`(Q타입 `QPaymentMethodEntity`), 기존 `ErrorCode`/`GetCheckoutOrdererDao`/`OrdererView`
- Produces (Task 4가 사용):
  - `PaymentsErrorCode.CHECKOUT_PRODUCT_NOT_FOUND` ("PAYMENTS-001", 404)
  - `CheckoutView(orderer: OrdererView, paymentMethods: PaymentMethodViews)`
  - `PaymentMethodViews(values: List<PaymentMethodView>)`, `PaymentMethodView(code: String, name: String)`
  - `GetCheckoutUseCase.getCheckout(userId: Long): CheckoutView` (시그니처 불변, 반환 내용 확장)

- [ ] **Step 1: PaymentsErrorCode 작성**

`meeple-core/src/main/kotlin/com/org/meeple/core/payments/PaymentsErrorCode.kt` 전체 내용:

```kotlin
package com.org.meeple.core.payments

import com.org.meeple.core.common.error.ErrorCode
import org.springframework.http.HttpStatus

/**
 * 결제(payments) 도메인 에러 코드.
 * [com.org.meeple.core.common.error.BusinessException]에 넘겨 사용한다.
 */
enum class PaymentsErrorCode(
	override val code: String,
	override val message: String,
	override val status: HttpStatus,
) : ErrorCode {

	/** 체크아웃 대상 일정을 모임에서 찾지 못함(scheduleId 미매칭). */
	CHECKOUT_PRODUCT_NOT_FOUND("PAYMENTS-001", "결제할 일정을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
}
```

- [ ] **Step 2: 결제수단 read model·일급 컬렉션·dao 작성**

`PaymentMethodView.kt`:

```kotlin
package com.org.meeple.core.payments.query.dto

/** 체크아웃 화면에 노출할 결제수단 한 건(read model). code는 프론트 계약 문자열(예: "BANK_TRANSFER")이다. */
data class PaymentMethodView(
	val code: String,
	val name: String,
)
```

`PaymentMethodViews.kt`:

```kotlin
package com.org.meeple.core.payments.query.dto

/** 결제수단 read model 일급 컬렉션. 노출 순서(displayOrder asc, id asc)가 유지된 목록을 담는다. */
data class PaymentMethodViews(
	val values: List<PaymentMethodView>,
)
```

`GetPaymentMethodDao.kt`:

```kotlin
package com.org.meeple.core.payments.query.dao

import com.org.meeple.core.payments.query.dto.PaymentMethodViews

/**
 * 결제수단 조회 dao(out-port). infra의 GetPaymentMethodDaoImpl이 구현한다.
 * 활성(active) 수단만 노출 순서(displayOrder asc, id asc)로 투영한다.
 */
interface GetPaymentMethodDao {

	fun findActiveMethods(): PaymentMethodViews
}
```

- [ ] **Step 3: CheckoutView·GetCheckoutService 확장**

`CheckoutView.kt` 전체 교체:

```kotlin
package com.org.meeple.core.payments.query.dto

/**
 * 체크아웃(결제) 화면 진입 시 조회 데이터 read model — payments 도메인이 소유한 부분(주문자·결제수단).
 * 상품(모임 일정) 정보는 gathering 도메인 in-port로 컨트롤러가 별도 조합한다.
 */
data class CheckoutView(
	val orderer: OrdererView,
	val paymentMethods: PaymentMethodViews,
)
```

`GetCheckoutService.kt` 전체 교체:

```kotlin
package com.org.meeple.core.payments.query.service

import com.org.meeple.core.payments.query.dao.GetCheckoutOrdererDao
import com.org.meeple.core.payments.query.dao.GetPaymentMethodDao
import com.org.meeple.core.payments.query.dto.CheckoutView
import com.org.meeple.core.payments.query.dto.OrdererView
import com.org.meeple.core.payments.query.service.port.`in`.GetCheckoutUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 체크아웃 화면 조회 서비스. payments 도메인이 소유한 주문자 정보·활성 결제수단을 read model로 반환한다.
 * 주문자 정보 미비(본인인증 전 등)는 화면 진입을 막을 사유가 아니므로 null 필드로 내려주고 예외를 던지지 않는다.
 */
@Service
@Transactional(readOnly = true)
class GetCheckoutService(
	private val getCheckoutOrdererDao: GetCheckoutOrdererDao,
	private val getPaymentMethodDao: GetPaymentMethodDao,
) : GetCheckoutUseCase {

	override fun getCheckout(userId: Long): CheckoutView =
		CheckoutView(
			orderer = getCheckoutOrdererDao.findOrdererByUserId(userId) ?: OrdererView.empty(),
			paymentMethods = getPaymentMethodDao.findActiveMethods(),
		)
}
```

- [ ] **Step 4: GetPaymentMethodDaoImpl 작성**

`meeple-infra/src/main/kotlin/com/org/meeple/infra/payments/query/GetPaymentMethodDaoImpl.kt` 전체 내용:

```kotlin
package com.org.meeple.infra.payments.query

import com.org.meeple.core.payments.query.dao.GetPaymentMethodDao
import com.org.meeple.core.payments.query.dto.PaymentMethodView
import com.org.meeple.core.payments.query.dto.PaymentMethodViews
import com.org.meeple.infra.payments.command.entity.QPaymentMethodEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * 결제수단 조회 dao([GetPaymentMethodDao])의 QueryDSL 구현.
 * 활성 수단만 노출 순서대로 [PaymentMethodView]로 직접 투영한다. (참조 데이터 전행 조회라 인덱스 불필요)
 */
@Component
class GetPaymentMethodDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetPaymentMethodDao {

	override fun findActiveMethods(): PaymentMethodViews {
		val paymentMethod: QPaymentMethodEntity = QPaymentMethodEntity.paymentMethodEntity
		val views: List<PaymentMethodView> = queryFactory
			.select(Projections.constructor(PaymentMethodView::class.java, paymentMethod.code, paymentMethod.name))
			.from(paymentMethod)
			.where(paymentMethod.active.isTrue)
			.orderBy(paymentMethod.displayOrder.asc(), paymentMethod.id.asc())
			.fetch()
		return PaymentMethodViews(values = views)
	}
}
```

- [ ] **Step 5: 컴파일 확인**

Run: `./gradlew :meeple-core:compileKotlin :meeple-infra:compileKotlin`
Expected: BUILD SUCCESSFUL. **커밋하지 않는다** (Task 4에서 일괄).

---

### Task 4: api 컨트롤러·응답 확장 + E2E GREEN + feat 커밋

**Files:**
- Modify: `meeple-api/src/main/kotlin/com/org/meeple/api/payments/PaymentsController.kt`
- Modify: `meeple-api/src/main/kotlin/com/org/meeple/api/payments/response/CheckoutResponse.kt`
- Create: `meeple-api/src/main/kotlin/com/org/meeple/api/payments/response/ProductResponse.kt`
- Create: `meeple-api/src/main/kotlin/com/org/meeple/api/payments/response/PaymentMethodResponse.kt`

**Interfaces:**
- Consumes: Task 2의 `GatheringScheduleView` 금액 메서드·`GatheringDetailView.scheduleOrNull`, Task 3의 `CheckoutView`/`PaymentMethodViews`/`PaymentsErrorCode`, 기존 `GetGatheringsUseCase`/`BusinessException`/`ApiResponse`
- Produces: 확장된 `GET /payments/v1/checkout` (Task 1의 E2E를 GREEN으로)

- [ ] **Step 1: ProductResponse·PaymentMethodResponse 작성**

`ProductResponse.kt` 전체 내용:

```kotlin
package com.org.meeple.api.payments.response

import com.org.meeple.common.user.Gender
import com.org.meeple.core.gathering.query.dto.GatheringDetailView
import com.org.meeple.core.gathering.query.dto.GatheringScheduleView
import java.time.LocalDateTime

/**
 * 체크아웃 상품(모임 일정) 응답. 금액은 정산형으로 내려준다:
 * [price] = 해당 성별 정가, [salePrice] = 서버 확정 실결제가(얼리버드 유효 → 얼리버드가, 소진 → 할인가, 그 외 정가).
 * 할인액 표시는 프론트가 price - salePrice로 계산한다. 매진([soldOut])이어도 조회는 막지 않는다.
 */
data class ProductResponse(
	val gatheringId: Long,
	val scheduleId: Long,
	val gender: Gender,
	val title: String,
	val imageUrl: String?,
	val region: String,
	val startAt: LocalDateTime,
	val price: Int,
	val salePrice: Int,
	val soldOut: Boolean,
) {
	companion object {
		fun of(gathering: GatheringDetailView, schedule: GatheringScheduleView, gender: Gender): ProductResponse =
			ProductResponse(
				gatheringId = gathering.id,
				scheduleId = schedule.id,
				gender = gender,
				title = gathering.title,
				imageUrl = gathering.imageUrl,
				region = gathering.region,
				startAt = schedule.startAt,
				price = schedule.feeFor(gender),
				salePrice = schedule.salePriceFor(gender),
				soldOut = schedule.soldOutFor(gender),
			)
	}
}
```

`PaymentMethodResponse.kt` 전체 내용:

```kotlin
package com.org.meeple.api.payments.response

import com.org.meeple.core.payments.query.dto.PaymentMethodView
import com.org.meeple.core.payments.query.dto.PaymentMethodViews

/** 체크아웃 결제수단 응답 한 건. 활성 수단만 노출 순서대로 내려간다. */
data class PaymentMethodResponse(
	val code: String,
	val name: String,
) {
	companion object {
		fun listOf(views: PaymentMethodViews): List<PaymentMethodResponse> =
			views.values.map { view: PaymentMethodView ->
				PaymentMethodResponse(code = view.code, name = view.name)
			}
	}
}
```

- [ ] **Step 2: CheckoutResponse 확장**

`CheckoutResponse.kt` 전체 교체:

```kotlin
package com.org.meeple.api.payments.response

import com.org.meeple.common.user.Gender
import com.org.meeple.core.gathering.query.dto.GatheringDetailView
import com.org.meeple.core.gathering.query.dto.GatheringScheduleView
import com.org.meeple.core.payments.query.dto.CheckoutView

/** 체크아웃(결제) 화면 진입 시 조회 데이터 응답 — 주문자·상품·결제수단. */
data class CheckoutResponse(
	val orderer: OrdererResponse,
	val product: ProductResponse,
	val paymentMethods: List<PaymentMethodResponse>,
) {
	companion object {
		fun of(
			view: CheckoutView,
			gathering: GatheringDetailView,
			schedule: GatheringScheduleView,
			gender: Gender,
		): CheckoutResponse =
			CheckoutResponse(
				orderer = OrdererResponse.of(view.orderer),
				product = ProductResponse.of(gathering, schedule, gender),
				paymentMethods = PaymentMethodResponse.listOf(view.paymentMethods),
			)
	}
}
```

- [ ] **Step 3: PaymentsController 확장**

`PaymentsController.kt` 전체 교체:

```kotlin
package com.org.meeple.api.payments

import com.org.meeple.api.payments.response.CheckoutResponse
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.common.user.Gender
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.common.response.ApiResponse
import com.org.meeple.core.gathering.query.dto.GatheringDetailView
import com.org.meeple.core.gathering.query.dto.GatheringScheduleView
import com.org.meeple.core.gathering.query.service.port.`in`.GetGatheringsUseCase
import com.org.meeple.core.payments.PaymentsErrorCode
import com.org.meeple.core.payments.query.dto.CheckoutView
import com.org.meeple.core.payments.query.service.port.`in`.GetCheckoutUseCase
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "결제", description = "결제(체크아웃) 화면 데이터 조회")
@RestController
@RequestMapping("/payments/v1")
class PaymentsController(
	private val getCheckoutUseCase: GetCheckoutUseCase,
	private val getGatheringsUseCase: GetGatheringsUseCase,
) {

	/**
	 * 결제 화면 진입 시 필요한 주문자 정보·상품(모임 일정) 정보·활성 결제수단을 조회한다.
	 * 상품은 gathering 도메인 in-port로 조합한다. 매진은 soldOut 플래그로 내려주고 차단하지 않는다.
	 */
	@Operation(
		summary = "체크아웃 화면 조회",
		description = "결제 화면 진입 시 필요한 주문자 정보(실명·이메일·휴대폰), 상품 정보(모임·일정·정가·실결제가·매진 여부), 활성 결제수단 목록을 조회한다. 본인인증 전 사용자는 주문자 필드가 null일 수 있다.",
	)
	@GetMapping("/checkout")
	fun getCheckout(
		@LoginUser user: AuthUser,
		@RequestParam gatheringId: Long,
		@RequestParam scheduleId: Long,
		@RequestParam gender: Gender,
	): ApiResponse<CheckoutResponse> {
		val checkout: CheckoutView = getCheckoutUseCase.getCheckout(user.id)
		val gathering: GatheringDetailView = getGatheringsUseCase.getGathering(gatheringId)
		val schedule: GatheringScheduleView = gathering.scheduleOrNull(scheduleId)
			?: throw BusinessException(PaymentsErrorCode.CHECKOUT_PRODUCT_NOT_FOUND)
		return ApiResponse.success(CheckoutResponse.of(checkout, gathering, schedule, gender))
	}
}
```

- [ ] **Step 4: E2E 실행해 GREEN 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.payments.PaymentsCheckoutE2ETest"`
Expected: PASS (8개 케이스 모두)

- [ ] **Step 5: offline 회귀 재확인 + 전체 컴파일**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.offline.OfflineGatheringDetailE2ETest" :meeple-core:compileKotlin :meeple-infra:compileKotlin`
Expected: PASS / BUILD SUCCESSFUL

- [ ] **Step 6: feat 커밋 (Task 1·3·4의 payments 변경 일괄)**

```bash
git add meeple-core/src/main/kotlin/com/org/meeple/core/payments \
        meeple-infra/src/main/kotlin/com/org/meeple/infra/payments \
        meeple-api/src/main/kotlin/com/org/meeple/api/payments \
        meeple-api/src/test/kotlin/com/org/meeple/api/payments \
        meeple-infra/src/testFixtures/kotlin/com/org/meeple/infra/fixture/PaymentMethodEntityFixture.kt
git commit -m "feat(payments): 체크아웃 응답에 상품 정보·결제수단 추가

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

커밋 후 `git status`로 워킹트리가 깨끗한지 확인한다.
