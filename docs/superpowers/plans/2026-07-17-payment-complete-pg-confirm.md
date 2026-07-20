# PG 승인 전제 결제완료(complete) 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 결제완료 `POST /payments/v1/complete`에 PG 최종 승인(confirm) 단계를 붙인다 — 좌석을 먼저 확보하고, PG 승인 성공 시 결제 기록을 남기며, 승인 실패 시 좌석을 복원한다.

**Architecture:** PG를 core out-port(`PaymentGatewayPort`)로 추상화하고 infra에 stub 어댑터(`@Profile("!prod")`, 요청 헤더로 성공/실패 제어)를 둔다. `CompletePaymentService`는 `@Transactional`을 벗고 오케스트레이터가 되어 ①좌석 확보(자기 트랜잭션) → ②PG confirm(트랜잭션 밖) → ③성공 시 결제 기록 저장 / 실패 시 좌석 복원 보상을 조율한다.

**Tech Stack:** Kotlin 2.2 / Spring Boot 4 / Spring Data JPA / Kotest(도메인 유닛) / RestAssured + Testcontainers(E2E).

## Global Constraints

- 설계 문서: `docs/superpowers/specs/2026-07-17-payment-complete-pg-confirm-design.md`.
- 응답·주석·커밋 메시지는 **한국어**. 커밋 형식 `<type>(<domain>): <설명>`.
- **타입 명시**: 변수·반환 타입·람다 파라미터 타입을 생략하지 않는다.
- **헥사고날 경계**: Controller→in-port(UseCase), Service→out-port. 도메인 규칙은 도메인 모델 메서드로 캡슐화(서비스에 `if…throw` 나열 금지).
- **CQRS**: 명령 서비스 `@Transactional`, 조회 `@Transactional(readOnly = true)`. 단, `CompletePaymentService`는 외부 호출을 포함하는 오케스트레이터라 클래스 트랜잭션을 두지 않고 각 협력자가 트랜잭션을 소유한다.
- **금액 근거**: confirm 금액은 `register()`가 반환한 서버 확정가(`registered.amount`). 클라이언트 금액을 신뢰하지 않는다.
- confirm 성공 후에도 참가 상태는 **PENDING 유지**(어드민 승인 존치).

---

## File Structure

**신규**
- `oneulsogae-core/.../payments/command/application/port/out/PaymentGatewayPort.kt` — PG 승인 out-port.
- `oneulsogae-infra/.../payments/command/adapter/StubPaymentGatewayAdapter.kt` — stub 구현(비-prod, 헤더 제어).
- `oneulsogae-core/.../gathering/command/application/port/in/ReleaseGatheringSeatUseCase.kt` — 좌석 복원 in-port.
- `oneulsogae-core/.../gathering/command/application/ReleaseGatheringSeatService.kt` — 좌석 복원 서비스.

**변경**
- `oneulsogae-core/.../gathering/command/domain/JoiningSchedule.kt` — `restore(gender, earlyBirdApplied)` 추가.
- `oneulsogae-core/.../gathering/command/domain/GatheringMember.kt` — `cancel()` 추가.
- `oneulsogae-core/.../payments/PaymentsErrorCode.kt` — `PAYMENT_CONFIRM_FAILED` 추가.
- `oneulsogae-core/.../payments/command/domain/Payment.kt` — `paymentKey` 추가.
- `oneulsogae-core/.../payments/command/application/port/in/command/CompletePaymentCommand.kt` — `paymentKey` 추가.
- `oneulsogae-api/.../payments/request/CompletePaymentRequest.kt` — `paymentKey` 추가.
- `oneulsogae-infra/.../payments/command/entity/PaymentEntity.kt` — `payment_key` 컬럼.
- `oneulsogae-infra/.../payments/command/adapter/PaymentAdapter.kt` — `paymentKey` 매핑.
- `docs/migration/payments.sql` — `payment_key` 컬럼.
- `oneulsogae-core/.../payments/command/application/CompletePaymentService.kt` — confirm 오케스트레이션.
- `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/common/integration/RestAssuredDsl.kt` — 커스텀 헤더 메서드.
- `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/payments/PaymentsCompleteE2ETest.kt` — paymentKey·confirm 케이스.
- `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/gathering/JoiningScheduleTest.kt` — `restore` 유닛.
- (신규 test) `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/gathering/GatheringMemberTest.kt` — `cancel` 유닛.

---

## Task 1: PaymentGatewayPort + Stub 어댑터 + E2E 헤더 DSL

**Files:**
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments/command/application/port/out/PaymentGatewayPort.kt`
- Create: `oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/payments/command/adapter/StubPaymentGatewayAdapter.kt`
- Modify: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/common/integration/RestAssuredDsl.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/infra/payments/StubPaymentGatewayAdapterTest.kt` (신규, 순수 로직 유닛)

**Interfaces:**
- Produces: `PaymentGatewayPort.confirm(paymentKey: String, amount: Int): Boolean`; `StubPaymentGatewayAdapter.confirmResult(header: String?): Boolean`; `StubPaymentGatewayAdapter.STUB_HEADER: String`; DSL `HttpRequestSpec.header(name: String, value: String)`.

- [ ] **Step 1: 헤더→승인결과 매핑 실패 테스트 작성**

Create `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/infra/payments/StubPaymentGatewayAdapterTest.kt`:

```kotlin
package com.org.oneulsogae.infra.payments

import com.org.oneulsogae.infra.payments.command.adapter.StubPaymentGatewayAdapter
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class StubPaymentGatewayAdapterTest : DescribeSpec({

	describe("confirmResult") {
		it("헤더가 fail이면 승인 실패(false)") {
			StubPaymentGatewayAdapter.confirmResult("fail") shouldBe false
		}
		it("헤더가 없으면 승인 성공(true)") {
			StubPaymentGatewayAdapter.confirmResult(null) shouldBe true
		}
		it("헤더가 fail이 아니면 승인 성공(true)") {
			StubPaymentGatewayAdapter.confirmResult("anything") shouldBe true
		}
	}
})
```

- [ ] **Step 2: 컴파일 실패 확인**

Run: `./gradlew :oneulsogae-api:compileTestKotlin`
Expected: FAIL — `StubPaymentGatewayAdapter` / `confirmResult` 미해결.

- [ ] **Step 3: 포트와 stub 어댑터 구현**

Create `PaymentGatewayPort.kt`:

```kotlin
package com.org.oneulsogae.core.payments.command.application.port.out

/** PG 최종 승인(confirm) 아웃포트. 좌석 확보 후 서버 확정 금액으로 결제를 승인한다. */
interface PaymentGatewayPort {

	/** [paymentKey] 거래를 [amount]원으로 승인한다. 성공이면 true, 실패면 false. */
	fun confirm(paymentKey: String, amount: Int): Boolean
}
```

Create `StubPaymentGatewayAdapter.kt`:

```kotlin
package com.org.oneulsogae.infra.payments.command.adapter

import com.org.oneulsogae.core.payments.command.application.port.out.PaymentGatewayPort
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * [PaymentGatewayPort]의 스텁 구현. 실제 PG 없이 승인을 흉내낸다. (연동 전 단계용)
 * 요청 헤더 X-Stub-Pg-Confirm=fail이면 승인 실패, 없거나 그 외면 성공을 반환한다.
 * prod는 실제 PG 어댑터(@Profile("prod"), 미구현)가 담당한다.
 */
@Component
@Profile("!prod")
class StubPaymentGatewayAdapter : PaymentGatewayPort {

	override fun confirm(paymentKey: String, amount: Int): Boolean =
		confirmResult(stubHeader())

	private fun stubHeader(): String? =
		(RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes)?.request?.getHeader(STUB_HEADER)

	companion object {

		const val STUB_HEADER: String = "X-Stub-Pg-Confirm"

		/** 헤더 값이 "fail"이면 승인 실패(false), 그 외/없으면 성공(true). */
		fun confirmResult(header: String?): Boolean = header != "fail"
	}
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.infra.payments.StubPaymentGatewayAdapterTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: E2E 요청 DSL에 커스텀 헤더 메서드 추가**

Modify `RestAssuredDsl.kt` — `HttpRequestSpec`의 `noRedirect()` 위(또는 `jsonBody` 아래)에 추가:

```kotlin
	/** 임의 요청 헤더를 추가한다. (예: 스텁 제어용 X-Stub-Pg-Confirm) */
	fun header(name: String, value: String) {
		spec.header(name, value)
	}
```

- [ ] **Step 6: 컴파일 확인 후 커밋**

Run: `./gradlew :oneulsogae-api:compileTestKotlin`
Expected: SUCCESS.

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments/command/application/port/out/PaymentGatewayPort.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/payments/command/adapter/StubPaymentGatewayAdapter.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/infra/payments/StubPaymentGatewayAdapterTest.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/common/integration/RestAssuredDsl.kt
git commit -m "feat(payments): PG 승인 out-port와 stub 어댑터(헤더 제어) 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: 좌석 복원 (도메인 + in-port/service)

**Files:**
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/gathering/command/domain/JoiningSchedule.kt`
- Modify: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/gathering/command/domain/GatheringMember.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/gathering/command/application/port/in/ReleaseGatheringSeatUseCase.kt`
- Create: `oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/gathering/command/application/ReleaseGatheringSeatService.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/gathering/JoiningScheduleTest.kt` (restore 추가)
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/gathering/GatheringMemberTest.kt` (신규)

**Interfaces:**
- Consumes: 기존 `GetJoiningSchedulePort.getForUpdate(scheduleId): JoiningSchedule?`, `SaveJoiningSchedulePort.save(schedule)`, `LoadGatheringMemberPort.loadByScheduleIdAndUserId(scheduleId, userId): GatheringMember?`, `SaveGatheringMemberPort.save(member)`.
- Produces: `JoiningSchedule.restore(gender: Gender, earlyBirdApplied: Boolean)`; `GatheringMember.cancel()`; `ReleaseGatheringSeatUseCase.release(scheduleId: Long, userId: Long)`.

- [ ] **Step 1: JoiningSchedule.restore 실패 테스트 작성**

Modify `JoiningScheduleTest.kt` — `describe("register")` 블록 뒤에 새 `describe` 추가:

```kotlin
	describe("restore") {

		it("얼리버드로 접수한 자리를 되돌리면 성별·얼리버드 여분이 모두 복원된다") {
			val target: JoiningSchedule = schedule(earlyBirdRemaining = 1, earlyBirdMaleFee = 7000)
			target.register(Gender.MALE, GatheringProductType.EARLY_BIRD) // male 3, eb 0

			target.restore(Gender.MALE, earlyBirdApplied = true)

			target.maleRemaining shouldBe 4
			target.earlyBirdRemaining shouldBe 1
		}

		it("정가로 접수한 자리를 되돌리면 성별 여분만 복원하고 얼리버드는 건드리지 않는다") {
			val target: JoiningSchedule = schedule(earlyBirdRemaining = 2)
			target.register(Gender.FEMALE, GatheringProductType.NORMAL) // female 3

			target.restore(Gender.FEMALE, earlyBirdApplied = false)

			target.femaleRemaining shouldBe 4
			target.earlyBirdRemaining shouldBe 2
		}
	}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.gathering.JoiningScheduleTest"`
Expected: FAIL — `restore` 미해결(컴파일 실패).

- [ ] **Step 3: JoiningSchedule.restore 구현**

Modify `JoiningSchedule.kt` — `register(...)` 메서드 바로 뒤에 추가:

```kotlin
	/** [register]로 차감한 여분을 되돌린다(PG 승인 실패 보상). 성별 여분 +1, [earlyBirdApplied]면 얼리버드 여분도 +1. */
	fun restore(gender: Gender, earlyBirdApplied: Boolean) {
		if (gender == Gender.MALE) maleRemaining += 1 else femaleRemaining += 1
		if (earlyBirdApplied) {
			earlyBirdRemaining = checkNotNull(earlyBirdRemaining) + 1
		}
	}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.gathering.JoiningScheduleTest"`
Expected: PASS.

- [ ] **Step 5: GatheringMember.cancel 실패 테스트 작성**

Create `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/gathering/GatheringMemberTest.kt`:

```kotlin
package com.org.oneulsogae.domain.gathering

import com.org.oneulsogae.common.gathering.GatheringMemberStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.gathering.command.domain.GatheringMember
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class GatheringMemberTest : DescribeSpec({

	describe("cancel") {
		it("승인대기 접수를 취소하면 참가취소(CANCELED)로 전이한다") {
			val member: GatheringMember = GatheringMember.pending(
				gatheringId = 10L,
				scheduleId = 1L,
				userId = 100L,
				gender = Gender.MALE,
				earlyBirdApplied = true,
			)

			member.cancel()

			member.status shouldBe GatheringMemberStatus.CANCELED
		}
	}
})
```

- [ ] **Step 6: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.gathering.GatheringMemberTest"`
Expected: FAIL — `cancel` 미해결.

- [ ] **Step 7: GatheringMember.cancel 구현**

Modify `GatheringMember.kt` — `revive(...)` 뒤에 추가:

```kotlin
	/** PG 승인 실패로 방금 접수를 취소한다(→ 참가취소). 차감 여분 복원은 서비스가 일정 도메인으로 처리한다. */
	fun cancel() {
		this.status = GatheringMemberStatus.CANCELED
	}
```

- [ ] **Step 8: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.domain.gathering.GatheringMemberTest"`
Expected: PASS.

- [ ] **Step 9: 좌석 복원 in-port·service 구현**

Create `ReleaseGatheringSeatUseCase.kt`:

```kotlin
package com.org.oneulsogae.core.gathering.command.application.port.`in`

/**
 * 확보한 좌석을 되돌리는 인포트. 결제완료(payments)가 PG 승인 실패 시 보상으로 호출한다.
 * 방금 접수한 참가를 취소(CANCELED)하고 차감했던 일정 여분(성별·얼리버드)을 복원한다.
 */
interface ReleaseGatheringSeatUseCase {

	fun release(scheduleId: Long, userId: Long)
}
```

Create `ReleaseGatheringSeatService.kt`:

```kotlin
package com.org.oneulsogae.core.gathering.command.application

import com.org.oneulsogae.core.gathering.command.application.port.`in`.ReleaseGatheringSeatUseCase
import com.org.oneulsogae.core.gathering.command.application.port.out.GetJoiningSchedulePort
import com.org.oneulsogae.core.gathering.command.application.port.out.LoadGatheringMemberPort
import com.org.oneulsogae.core.gathering.command.application.port.out.SaveGatheringMemberPort
import com.org.oneulsogae.core.gathering.command.application.port.out.SaveJoiningSchedulePort
import com.org.oneulsogae.core.gathering.command.domain.GatheringMember
import com.org.oneulsogae.core.gathering.command.domain.JoiningSchedule
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [ReleaseGatheringSeatUseCase] 구현. (명령 보상)
 * 접수 시 잠근 것과 같은 경로(getForUpdate)로 일정을 잠그고 여분을 복원한 뒤 참가를 취소한다.
 * 방금 접수한 직후 호출되므로 대상 행이 없으면 시스템 오류로 본다.
 */
@Service
@Transactional
class ReleaseGatheringSeatService(
	private val loadGatheringMemberPort: LoadGatheringMemberPort,
	private val saveGatheringMemberPort: SaveGatheringMemberPort,
	private val getJoiningSchedulePort: GetJoiningSchedulePort,
	private val saveJoiningSchedulePort: SaveJoiningSchedulePort,
) : ReleaseGatheringSeatUseCase {

	override fun release(scheduleId: Long, userId: Long) {
		val member: GatheringMember = loadGatheringMemberPort.loadByScheduleIdAndUserId(scheduleId, userId)
			?: throw IllegalStateException("복원할 참가자를 찾을 수 없습니다: schedule=$scheduleId, user=$userId")
		val schedule: JoiningSchedule = getJoiningSchedulePort.getForUpdate(member.scheduleId)
			?: throw IllegalStateException("모임 일정을 찾을 수 없습니다: ${member.scheduleId}")
		schedule.restore(member.gender, member.earlyBirdApplied)
		member.cancel()
		saveGatheringMemberPort.save(member)
		saveJoiningSchedulePort.save(schedule)
	}
}
```

- [ ] **Step 10: 컴파일 확인 후 커밋**

Run: `./gradlew :oneulsogae-core:compileKotlin :oneulsogae-api:test --tests "com.org.oneulsogae.domain.gathering.*"`
Expected: SUCCESS, 관련 유닛 PASS.

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/gathering/command/domain/JoiningSchedule.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/gathering/command/domain/GatheringMember.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/gathering/command/application/port/in/ReleaseGatheringSeatUseCase.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/gathering/command/application/ReleaseGatheringSeatService.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/gathering/JoiningScheduleTest.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/domain/gathering/GatheringMemberTest.kt
git commit -m "feat(gathering): PG 승인 실패 보상용 좌석 복원(release) 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: paymentKey 배선 (DDL·엔티티·도메인·요청·저장)

이 태스크까지는 confirm을 넣지 않고 `paymentKey`만 흘려 저장한다(무검증 기존 흐름 유지). confirm 통합은 Task 4.

**Files:**
- Modify: `docs/migration/payments.sql`
- Modify: `oneulsogae-infra/.../payments/command/entity/PaymentEntity.kt`
- Modify: `oneulsogae-core/.../payments/command/domain/Payment.kt`
- Modify: `oneulsogae-infra/.../payments/command/adapter/PaymentAdapter.kt`
- Modify: `oneulsogae-core/.../payments/command/application/port/in/command/CompletePaymentCommand.kt`
- Modify: `oneulsogae-api/.../payments/request/CompletePaymentRequest.kt`
- Modify: `oneulsogae-core/.../payments/command/application/CompletePaymentService.kt`
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/payments/PaymentsCompleteE2ETest.kt`

**Interfaces:**
- Produces: `Payment(..., paymentKey: String)`; `CompletePaymentCommand(productId, paymentKey)`; `PaymentEntity.paymentKey`.

- [ ] **Step 1: E2E에 paymentKey 저장 기대 추가(실패 유도)**

Modify `PaymentsCompleteE2ETest.kt` — 첫 케이스(얼리버드 상품 결제)의 `saved` 검증 뒤에 추가:

```kotlin
				saved?.paymentKey shouldBe "pay_key_1"
```

그리고 그 케이스의 요청 본문을 paymentKey 포함으로 바꾼다:

```kotlin
				post("/payments/v1/complete") {
					bearer(accessTokenFor(userId))
					jsonBody("""{"productId": $earlyBirdProductId, "paymentKey": "pay_key_1"}""")
				} expect {
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.payments.PaymentsCompleteE2ETest"`
Expected: FAIL — `paymentKey` 미해결(컴파일) 또는 저장 안 됨.

- [ ] **Step 3: DDL·엔티티·도메인·어댑터에 paymentKey 추가**

Modify `docs/migration/payments.sql` — `product_id` 다음 줄에 추가:

```sql
    payment_key VARCHAR(255) NOT NULL,
```

Modify `PaymentEntity.kt` — `productId` 컬럼 뒤에 추가:

```kotlin
	/** PG 거래 식별자(paymentKey). 승인 성공 건만 저장된다. */
	@Column(name = "payment_key", nullable = false)
	val paymentKey: String,
```

Modify `Payment.kt` — 생성자 `amount` 뒤에 `paymentKey` 추가하고 KDoc 한 줄 보완:

```kotlin
class Payment(
	val id: Long? = null,
	val userId: Long,
	val gatheringId: Long,
	val scheduleId: Long,
	val productId: Long,
	val gender: Gender,
	val amount: Int,
	val paymentKey: String,
)
```

Modify `PaymentAdapter.kt` — `save`의 엔티티 생성·도메인 재구성 양쪽에 `paymentKey` 추가:

```kotlin
		val saved: PaymentEntity = paymentJpaRepository.save(
			PaymentEntity(
				userId = payment.userId,
				gatheringId = payment.gatheringId,
				scheduleId = payment.scheduleId,
				productId = payment.productId,
				gender = payment.gender,
				amount = payment.amount,
				paymentKey = payment.paymentKey,
			),
		)
		return Payment(
			id = saved.id,
			userId = saved.userId,
			gatheringId = saved.gatheringId,
			scheduleId = saved.scheduleId,
			productId = saved.productId,
			gender = saved.gender,
			amount = saved.amount,
			paymentKey = saved.paymentKey,
		)
```

- [ ] **Step 4: 요청·커맨드에 paymentKey 추가**

Modify `CompletePaymentCommand.kt`:

```kotlin
/** 결제완료 접수 명령. 상품은 productId로 지정하고, [paymentKey]는 PG 결제 인증 결과(거래 식별자)다. */
data class CompletePaymentCommand(
	val productId: Long,
	val paymentKey: String,
)
```

Modify `CompletePaymentRequest.kt`:

```kotlin
package com.org.oneulsogae.api.payments.request

import com.org.oneulsogae.core.payments.command.application.port.`in`.command.CompletePaymentCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

/** 결제완료 접수 요청. 상품은 productId로, PG 결제 인증 결과는 paymentKey로 지정한다. */
data class CompletePaymentRequest(
	@field:NotNull
	val productId: Long?,

	@field:NotBlank
	val paymentKey: String?,
) {

	fun toCommand(): CompletePaymentCommand =
		CompletePaymentCommand(productId = productId!!, paymentKey = paymentKey!!)
}
```

- [ ] **Step 5: CompletePaymentService가 paymentKey를 저장하도록 수정**

Modify `CompletePaymentService.kt` — `savePaymentPort.save(Payment(...))`에 `paymentKey = command.paymentKey` 추가:

```kotlin
		savePaymentPort.save(
			Payment(
				userId = userId,
				gatheringId = product.gatheringId,
				scheduleId = product.scheduleId,
				productId = command.productId,
				gender = gender,
				amount = registered.amount,
				paymentKey = command.paymentKey,
			),
		)
```

- [ ] **Step 6: E2E 기존 케이스 전부 paymentKey 포함으로 갱신**

Modify `PaymentsCompleteE2ETest.kt` — `jsonBody("""{"productId": ...}""")` 형태의 **모든** 호출에 `, "paymentKey": "<임의값>"` 추가(각 케이스 고유 문자열, 예 `"pay_key_normal"`, `"pay_key_soldout"` 등). 없는 productId 케이스도 동일하게 넣는다(검증 실패가 productId가 아닌 paymentKey NotBlank로 가려지지 않도록 productId는 유효 형식 유지).

- [ ] **Step 7: 테스트 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.payments.PaymentsCompleteE2ETest"`
Expected: PASS (전 케이스 + paymentKey 저장 확인).

- [ ] **Step 8: 커밋**

```bash
git add docs/migration/payments.sql \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/payments/command/entity/PaymentEntity.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments/command/domain/Payment.kt \
        oneulsogae-infra/src/main/kotlin/com/org/oneulsogae/infra/payments/command/adapter/PaymentAdapter.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments/command/application/port/in/command/CompletePaymentCommand.kt \
        oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/payments/request/CompletePaymentRequest.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments/command/application/CompletePaymentService.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/payments/PaymentsCompleteE2ETest.kt
git commit -m "feat(payments): 결제완료 요청·기록에 paymentKey 추가

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: confirm 오케스트레이션 통합

**Files:**
- Modify: `oneulsogae-core/.../payments/PaymentsErrorCode.kt`
- Modify: `oneulsogae-core/.../payments/command/application/CompletePaymentService.kt`
- Modify: `oneulsogae-api/.../payments/PaymentsController.kt` (스웨거 설명에 PAYMENTS-004 추가 — 선택)
- Test: `oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/payments/PaymentsCompleteE2ETest.kt`

**Interfaces:**
- Consumes: `PaymentGatewayPort.confirm(paymentKey, amount): Boolean` (Task 1), `ReleaseGatheringSeatUseCase.release(scheduleId, userId)` (Task 2).
- Produces: `PaymentsErrorCode.PAYMENT_CONFIRM_FAILED` ("PAYMENTS-004", 402).

- [ ] **Step 1: confirm 실패 시 좌석 복원 E2E 실패 테스트 작성**

Modify `PaymentsCompleteE2ETest.kt` — 새 context 추가(정가 상품 케이스 참고, 헬퍼 `persistUserWithGender`/`persistGatheringWithSchedule`/`findMember`/`findSchedule` 재사용):

```kotlin
		context("PG 승인(confirm)이 실패하면") {
			it("402 PAYMENTS-004를 반환하고 좌석·여분을 복원하며 결제 기록을 남기지 않는다") {
				val userId: Long = persistUserWithGender(providerId = "pay-complete-confirm-fail", gender = Gender.MALE)
				val (gatheringId: Long, scheduleId: Long, normalProductId: Long) = persistGatheringWithSchedule()

				post("/payments/v1/complete") {
					bearer(accessTokenFor(userId))
					header("X-Stub-Pg-Confirm", "fail")
					jsonBody("""{"productId": $normalProductId, "paymentKey": "pay_key_fail"}""")
				} expect {
					status(402)
					body("error.code", "PAYMENTS-004")
				}

				// 좌석 복원: 여분 원복, 참가는 CANCELED, 결제 기록 없음
				findSchedule(scheduleId)?.maleRemaining shouldBe 4
				findMember(scheduleId, userId)?.status shouldBe GatheringMemberStatus.CANCELED

				val payment: QPaymentEntity = QPaymentEntity.paymentEntity
				IntegrationUtil.getQuery().selectFrom(payment)
					.where(payment.scheduleId.eq(scheduleId), payment.userId.eq(userId))
					.fetchOne() shouldBe null
			}
		}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.payments.PaymentsCompleteE2ETest"`
Expected: FAIL — 현재는 confirm이 없어 200으로 접수됨(402 아님), 또는 `PAYMENT_CONFIRM_FAILED` 미해결.

- [ ] **Step 3: 에러코드 추가**

Modify `PaymentsErrorCode.kt` — `PAYMENT_PRODUCT_GENDER_MISMATCH` 뒤, `HttpStatus` import는 이미 있음:

```kotlin
	/** PG 최종 승인(confirm) 실패. 좌석은 복원되고 청구되지 않는다. */
	PAYMENT_CONFIRM_FAILED("PAYMENTS-004", "결제 승인에 실패했습니다. 다시 시도해주세요.", HttpStatus.PAYMENT_REQUIRED),
```

- [ ] **Step 4: CompletePaymentService에 confirm 오케스트레이션 통합**

Modify `CompletePaymentService.kt` — 클래스 `@Transactional` 제거, 생성자에 `PaymentGatewayPort`·`ReleaseGatheringSeatUseCase` 주입, register와 save 사이에 confirm/보상 삽입. 전체 파일:

```kotlin
package com.org.oneulsogae.core.payments.command.application

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.gathering.command.application.port.`in`.RegisterGatheringMemberUseCase
import com.org.oneulsogae.core.gathering.command.application.port.`in`.ReleaseGatheringSeatUseCase
import com.org.oneulsogae.core.gathering.command.application.port.`in`.command.RegisterGatheringMemberCommand
import com.org.oneulsogae.core.gathering.command.application.port.`in`.result.RegisterGatheringMemberResult
import com.org.oneulsogae.core.gathering.query.dto.GatheringProductIdentity
import com.org.oneulsogae.core.gathering.query.service.port.`in`.GetGatheringsUseCase
import com.org.oneulsogae.core.payments.PaymentsErrorCode
import com.org.oneulsogae.core.payments.command.application.port.`in`.CompletePaymentUseCase
import com.org.oneulsogae.core.payments.command.application.port.`in`.command.CompletePaymentCommand
import com.org.oneulsogae.core.payments.command.application.port.`in`.result.CompletePaymentResult
import com.org.oneulsogae.core.payments.command.application.port.out.PaymentGatewayPort
import com.org.oneulsogae.core.payments.command.application.port.out.SavePaymentPort
import com.org.oneulsogae.core.payments.command.domain.Payment
import com.org.oneulsogae.core.user.query.service.port.`in`.GetUserDetailUseCase
import org.springframework.stereotype.Service

/**
 * [CompletePaymentUseCase] 구현. (오케스트레이터 — 외부 호출을 포함해 클래스 트랜잭션을 두지 않는다)
 * ① 좌석 확보(RegisterGatheringMemberUseCase, 자기 트랜잭션): 소진·마감이면 여기서 실패해 PG 승인을 하지 않는다.
 * ② PG 최종 승인(PaymentGatewayPort.confirm, 트랜잭션 밖): 실패하면 확보한 좌석을 복원(보상)한다.
 * ③ 승인 성공 시 서버 확정가로 결제 기록을 저장한다.
 */
@Service
class CompletePaymentService(
	private val getUserDetailUseCase: GetUserDetailUseCase,
	private val getGatheringsUseCase: GetGatheringsUseCase,
	private val registerGatheringMemberUseCase: RegisterGatheringMemberUseCase,
	private val releaseGatheringSeatUseCase: ReleaseGatheringSeatUseCase,
	private val paymentGatewayPort: PaymentGatewayPort,
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

		// ① 좌석 확보 (자기 트랜잭션). 소진·마감이면 여기서 실패 → PG 승인 안 함 → 미청구.
		val registered: RegisterGatheringMemberResult = registerGatheringMemberUseCase.register(
			RegisterGatheringMemberCommand(
				gatheringId = product.gatheringId,
				scheduleId = product.scheduleId,
				userId = userId,
				gender = gender,
				type = product.type,
			),
		)

		// ② PG 최종 승인 (트랜잭션 밖). 실패 시 확보한 좌석 복원(보상) 후 402.
		val approved: Boolean = paymentGatewayPort.confirm(command.paymentKey, registered.amount)
		if (!approved) {
			releaseGatheringSeatUseCase.release(product.scheduleId, userId)
			throw BusinessException(PaymentsErrorCode.PAYMENT_CONFIRM_FAILED)
		}

		// ③ 결제 기록 저장.
		savePaymentPort.save(
			Payment(
				userId = userId,
				gatheringId = product.gatheringId,
				scheduleId = product.scheduleId,
				productId = command.productId,
				gender = gender,
				amount = registered.amount,
				paymentKey = command.paymentKey,
			),
		)
		return CompletePaymentResult(amount = registered.amount)
	}
}
```

- [ ] **Step 5: 스웨거 설명에 PAYMENTS-004 추가(선택)**

Modify `PaymentsController.kt` — `complete`의 `@Operation(description = ...)` 문자열 끝에 `"결제 승인 실패 402(PAYMENTS-004)."` 를 덧붙인다.

- [ ] **Step 6: 대상 E2E 통과 확인**

Run: `./gradlew :oneulsogae-api:test --tests "com.org.oneulsogae.api.payments.PaymentsCompleteE2ETest"`
Expected: PASS — confirm 실패 케이스(402·복원·미저장) 및 기존 케이스 전부.

- [ ] **Step 7: 전체 테스트로 회귀 확인**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: 커밋**

```bash
git add oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments/PaymentsErrorCode.kt \
        oneulsogae-core/src/main/kotlin/com/org/oneulsogae/core/payments/command/application/CompletePaymentService.kt \
        oneulsogae-api/src/main/kotlin/com/org/oneulsogae/api/payments/PaymentsController.kt \
        oneulsogae-api/src/test/kotlin/com/org/oneulsogae/api/payments/PaymentsCompleteE2ETest.kt
git commit -m "feat(payments): 결제완료에 PG 승인(confirm) 통합 — 좌석 먼저·실패 시 복원

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Self-Review

- **Spec 커버리지**: 흐름(§4)→Task4, 컴포넌트(§5)→Task1·2·4, 요청/응답(§6)→Task3, payments 변경(§7)→Task3, stub 제어(§8)→Task1, 정합성 한계(§9)→코드로 구현하지 않음(명시적 범위 밖, `savePayment` 실패·멱등 유니크는 계획에 태스크 없음 — 의도적), 테스트(§10)→각 Task. 모두 매핑됨.
- **Placeholder**: 없음(모든 스텝 실제 코드/명령).
- **타입 일관성**: `confirm(paymentKey: String, amount: Int): Boolean`, `release(scheduleId: Long, userId: Long)`, `restore(gender: Gender, earlyBirdApplied: Boolean)`, `cancel()`, `Payment(..., paymentKey: String)` — 태스크 간 일치.
- **주의(실행자)**: `CompletePaymentService`에서 `@Transactional`을 제거하므로 `import org.springframework.transaction.annotation.Transactional`도 함께 삭제한다(Task 4 Step 4의 전체 파일에는 이미 반영됨).
