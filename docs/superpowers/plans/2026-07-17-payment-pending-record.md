# 결제 PENDING 선저장 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** PG 승인 이전에 `paymentKey`를 담은 결제 기록을 `PENDING`으로 선저장하고, 승인 결과로 `APPROVED`/`FAILED`로 전이해 "청구됐는데 흔적 없음"을 제거한다.

**Architecture:** `CompletePaymentService`(트랜잭션 없는 오케스트레이터)의 순서를 `좌석확보 → PENDING 저장 → PG 승인 → 상태 전이`로 바꾼다. `payments`에 `status` 컬럼을 신설하고, 상태 전이용 아웃포트 `UpdatePaymentStatusPort`를 추가한다. `payment_key`에 유니크를 걸어 이중 기록을 DB 레벨에서 막는다.

**Tech Stack:** Kotlin 2.2.21 / Spring Boot 4 / Spring Data JPA / MySQL, 헥사고날(Ports & Adapters), Kotest + Testcontainers(E2E).

## Global Constraints

- 변수·반환·람다 파라미터 타입을 명시한다(표현식 본문 포함).
- Controller/Service는 in-port, Service는 out-port를 주입. Adapter(infra)가 out-port 구현.
- `ddl-auto: validate` — 엔티티와 실 DDL이 정확히 일치해야 한다.
- 상태 전이 enum `PaymentStatus { PENDING, APPROVED, FAILED }` (payments 전용, `meeple-common` 아님).
- `payments.status`는 PG 청구 라이프사이클 원장 — 참가 승인 원장 `gathering_members.status`와는 다른 축(승인 성공 후에도 좌석은 PENDING 유지).
- 응답 계약(`CompletePaymentResult`=amount) 불변 — 프론트엔드 대응 불필요.

---

### Task 1: status 플러밍 (컴파일 · 기존 동작 불변)

`PaymentStatus` enum, `Payment.status`, `PaymentEntity.status`(+payment_key 유니크), 어댑터의 양방향 매핑, `UpdatePaymentStatusPort`를 추가한다. 이 시점 서비스는 **여전히 승인 성공 시에만 저장**하되 `status=APPROVED`로 저장한다 — 동작은 바뀌지 않아 기존 E2E가 그대로 통과한다.

**Files:**
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/payments/command/domain/PaymentStatus.kt`
- Create: `meeple-core/src/main/kotlin/com/org/meeple/core/payments/command/application/port/out/UpdatePaymentStatusPort.kt`
- Modify: `meeple-core/src/main/kotlin/com/org/meeple/core/payments/command/domain/Payment.kt`
- Modify: `meeple-core/src/main/kotlin/com/org/meeple/core/payments/command/application/CompletePaymentService.kt`
- Modify: `meeple-infra/src/main/kotlin/com/org/meeple/infra/payments/command/entity/PaymentEntity.kt`
- Modify: `meeple-infra/src/main/kotlin/com/org/meeple/infra/payments/command/adapter/PaymentAdapter.kt`
- Modify: `docs/migration/payments.sql`

**Interfaces:**
- Produces:
  - `enum class PaymentStatus { PENDING, APPROVED, FAILED }` (패키지 `com.org.meeple.core.payments.command.domain`)
  - `Payment(..., val status: PaymentStatus)` — 생성자 마지막 파라미터로 `status` 추가(기본값 없음)
  - `interface UpdatePaymentStatusPort { fun updateStatus(paymentId: Long, status: PaymentStatus) }`
  - `PaymentAdapter : SavePaymentPort, UpdatePaymentStatusPort`

- [ ] **Step 1: `PaymentStatus` enum 생성**

Create `meeple-core/src/main/kotlin/com/org/meeple/core/payments/command/domain/PaymentStatus.kt`:

```kotlin
package com.org.meeple.core.payments.command.domain

/**
 * 결제(PG 청구) 라이프사이클 상태. 참가 승인 상태(gathering_members.status)와는 다른 축이다.
 */
enum class PaymentStatus {
	/** 결제 기록 생성 — PG 최종 승인 이전. */
	PENDING,

	/** PG 최종 승인 성공. */
	APPROVED,

	/** PG 최종 승인 실패. */
	FAILED,
}
```

- [ ] **Step 2: `Payment` 도메인에 status 추가**

Modify `meeple-core/.../payments/command/domain/Payment.kt` — 클래스 doc 한 줄과 생성자 파라미터를 교체한다.

doc의 `PG 최종 승인(confirm) 성공 건만 남기며,` 문장을 다음으로 바꾼다:

```kotlin
 * 결제 기록(command 도메인 모델). 접수 시점에 PENDING으로 저장하고 PG 승인 결과로 APPROVED/FAILED로 전이한다.
```

생성자에 `status`를 추가한다(기존 `paymentKey` 다음):

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
	val status: PaymentStatus,
)
```

(같은 패키지라 `PaymentStatus` import 불필요.)

- [ ] **Step 3: `UpdatePaymentStatusPort` 생성**

Create `meeple-core/.../payments/command/application/port/out/UpdatePaymentStatusPort.kt`:

```kotlin
package com.org.meeple.core.payments.command.application.port.out

import com.org.meeple.core.payments.command.domain.PaymentStatus

/** 결제 기록의 상태를 전이하는 아웃포트. PG 승인 결과로 PENDING → APPROVED/FAILED 전이에 쓴다. */
interface UpdatePaymentStatusPort {

	fun updateStatus(paymentId: Long, status: PaymentStatus)
}
```

- [ ] **Step 4: `PaymentEntity`에 status var + payment_key 유니크**

Modify `meeple-infra/.../payments/command/entity/PaymentEntity.kt`:

import에 추가:

```kotlin
import com.org.meeple.core.payments.command.domain.PaymentStatus
```

클래스 doc의 `PG 최종 승인(confirm) 성공 건만 저장한다 — ` 문구를 다음으로 교체:

```kotlin
 * 결제 기록 한 건. 접수 시점에 PENDING으로 저장하고 PG 승인 결과로 APPROVED/FAILED로 전이한다.
```

`payment_key` 필드에 `unique = true`를 추가하고 doc을 교체:

```kotlin
	/** PG 거래 식별자(paymentKey). PG 인증마다 고유 — 이중 제출의 이중 기록을 막기 위해 유니크다. */
	@Column(name = "payment_key", nullable = false, unique = true)
	val paymentKey: String,
```

생성자 마지막 필드(`amount` 다음)에 status를 추가:

```kotlin
	/** 서버 확정 실결제가(원). */
	@Column(name = "amount", nullable = false)
	val amount: Int,

	/** 결제(PG 청구) 라이프사이클 상태. */
	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, columnDefinition = "varchar(50)")
	var status: PaymentStatus,
) : BaseEntity()
```

(`Enumerated`, `EnumType`는 이미 import되어 있다.)

- [ ] **Step 5: `PaymentAdapter`에 status 매핑 + updateStatus 구현**

Modify `meeple-infra/.../payments/command/adapter/PaymentAdapter.kt` — 전체를 다음으로 교체:

```kotlin
package com.org.meeple.infra.payments.command.adapter

import com.org.meeple.core.payments.command.application.port.out.SavePaymentPort
import com.org.meeple.core.payments.command.application.port.out.UpdatePaymentStatusPort
import com.org.meeple.core.payments.command.domain.Payment
import com.org.meeple.core.payments.command.domain.PaymentStatus
import com.org.meeple.infra.payments.command.entity.PaymentEntity
import com.org.meeple.infra.payments.command.repository.PaymentJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * [PaymentEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나)
 * 결제 기록 저장([SavePaymentPort])·상태 전이([UpdatePaymentStatusPort]) out-port를 구현한다.
 */
@Component
class PaymentAdapter(
	private val paymentJpaRepository: PaymentJpaRepository,
) : SavePaymentPort, UpdatePaymentStatusPort {

	override fun save(payment: Payment): Payment {
		val saved: PaymentEntity = paymentJpaRepository.save(
			PaymentEntity(
				userId = payment.userId,
				gatheringId = payment.gatheringId,
				scheduleId = payment.scheduleId,
				productId = payment.productId,
				gender = payment.gender,
				amount = payment.amount,
				paymentKey = payment.paymentKey,
				status = payment.status,
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
			status = saved.status,
		)
	}

	@Transactional
	override fun updateStatus(paymentId: Long, status: PaymentStatus) {
		val entity: PaymentEntity = paymentJpaRepository.findById(paymentId)
			.orElseThrow { IllegalStateException("결제 기록을 찾을 수 없습니다: $paymentId") }
		entity.status = status
	}
}
```

- [ ] **Step 6: `CompletePaymentService`의 저장에 status=APPROVED 명시 (동작 불변)**

Modify `meeple-core/.../payments/command/application/CompletePaymentService.kt`.

import에 추가:

```kotlin
import com.org.meeple.core.payments.command.domain.PaymentStatus
```

③ 저장 블록의 `Payment(...)`에 `status`를 추가한다(기존 `paymentKey` 다음 줄):

```kotlin
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
				status = PaymentStatus.APPROVED,
			),
		)
```

- [ ] **Step 7: `payments.sql`에 status 컬럼 + 유니크 반영**

Modify `docs/migration/payments.sql` — 전체를 다음으로 교체:

```sql
-- 결제 기록 테이블. 접수 시점에 PENDING으로 저장하고 PG 최종 승인 결과로 APPROVED/FAILED로 전이한다.
-- status는 PG 청구 라이프사이클 원장이며, 참가 승인 원장(gathering_members.status)과는 다른 축이다.
CREATE TABLE payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    gathering_id BIGINT NOT NULL,
    schedule_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    payment_key VARCHAR(255) NOT NULL,
    gender VARCHAR(50) NOT NULL,
    amount INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) DEFAULT NULL,
    UNIQUE KEY uk_payment_key (payment_key),
    INDEX idx_schedule_id_user_id (schedule_id, user_id)
);
```

> 이미 payments 테이블이 반영된 환경이 있으면 위 대신 아래 ALTER를 쓴다(파일에는 CREATE 형태로 유지):
> ```sql
> ALTER TABLE payments
>   ADD COLUMN status VARCHAR(50) NOT NULL,
>   ADD UNIQUE KEY uk_payment_key (payment_key);
> ```

- [ ] **Step 8: 컴파일 확인**

Run: `./gradlew :meeple-infra:compileKotlin -q`
Expected: 성공(에러 없음).

- [ ] **Step 9: 기존 E2E가 그대로 통과하는지 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.payments.PaymentsCompleteE2ETest" -q`
Expected: PASS (동작 불변 — 저장 status만 APPROVED로 바뀌고 아직 아무 테스트도 status를 단언하지 않는다).

- [ ] **Step 10: 커밋**

```bash
git add meeple-core meeple-infra docs/migration/payments.sql
git commit -m "feat(payments): 결제 기록 status(PENDING/APPROVED/FAILED) 컬럼·상태전이 포트 추가"
```

---

### Task 2: PENDING 선저장 흐름으로 전환

`CompletePaymentService`를 `좌석확보 → PENDING 저장 → 승인 → 전이` 순서로 바꾸고, 승인 실패 시 `FAILED` 전이 + 좌석 복원 + 402를 수행한다. E2E를 먼저 갱신(실패 확인)한 뒤 서비스를 고쳐 통과시킨다.

**Files:**
- Modify: `meeple-api/src/test/kotlin/com/org/meeple/api/payments/PaymentsCompleteE2ETest.kt`
- Modify: `meeple-core/src/main/kotlin/com/org/meeple/core/payments/command/application/CompletePaymentService.kt`

**Interfaces:**
- Consumes: Task 1의 `UpdatePaymentStatusPort.updateStatus(paymentId: Long, status: PaymentStatus)`, `PaymentStatus`, `Payment(..., status)`.
- Consumes(기존): `ReleaseGatheringSeatUseCase.release(scheduleId: Long, userId: Long)`, `SavePaymentPort.save(payment): Payment`(id 채워 반환).

- [ ] **Step 1: E2E 실패 테스트로 갱신 (성공=APPROVED, 실패=FAILED)**

Modify `PaymentsCompleteE2ETest.kt`.

import 추가(기존 import 블록):

```kotlin
import com.org.meeple.core.payments.command.domain.PaymentStatus
```

(1) 성공 케이스(첫 번째 `it`, "얼리버드가로 PENDING 참가·결제 기록을 남기고 ..."): `saved?.paymentKey shouldBe "pay_key_1"` 다음 줄에 status 단언 추가:

```kotlin
					saved?.paymentKey shouldBe "pay_key_1"
					saved?.status shouldBe PaymentStatus.APPROVED
```

(2) confirm 실패 케이스(context "PG 승인(confirm)이 실패하면") 블록 전체를 다음으로 교체:

```kotlin
		context("PG 승인(confirm)이 실패하면") {
			it("402 PAYMENTS-004를 반환하고 좌석·여분을 복원하며 결제 기록을 FAILED로 남긴다") {
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

				// 좌석 복원: 여분 원복, 참가는 CANCELED. 결제 기록은 FAILED로 보존(이력 추적).
				findSchedule(scheduleId)?.maleRemaining shouldBe 4
				findMember(scheduleId, userId)?.status shouldBe GatheringMemberStatus.CANCELED

				val payment: QPaymentEntity = QPaymentEntity.paymentEntity
				val failed: PaymentEntity? = IntegrationUtil.getQuery().selectFrom(payment)
					.where(payment.scheduleId.eq(scheduleId), payment.userId.eq(userId))
					.fetchOne()
				failed?.status shouldBe PaymentStatus.FAILED
				failed?.paymentKey shouldBe "pay_key_fail"
			}
		}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.payments.PaymentsCompleteE2ETest" -q`
Expected: FAIL — 성공 케이스의 APPROVED 단언은 통과한다(Task 1에서 이미 성공 시 status=APPROVED로 저장). 그러나 confirm-fail 케이스는 현재 서비스가 실패 시 아무것도 저장하지 않아 `failed`가 null → `PaymentStatus.FAILED` 단언에서 실패.

- [ ] **Step 3: `CompletePaymentService`를 PENDING 선저장 흐름으로 교체**

Modify `CompletePaymentService.kt`.

생성자에 `updatePaymentStatusPort`를 주입(기존 `savePaymentPort` 다음):

```kotlin
import com.org.meeple.core.payments.command.application.port.out.UpdatePaymentStatusPort
```

```kotlin
	private val paymentGatewayPort: PaymentGatewayPort,
	private val savePaymentPort: SavePaymentPort,
	private val updatePaymentStatusPort: UpdatePaymentStatusPort,
) : CompletePaymentUseCase {
```

클래스 doc의 ①~③ 설명을 다음으로 교체:

```kotlin
/**
 * [CompletePaymentUseCase] 구현. (오케스트레이터 — 외부 호출을 포함해 클래스 트랜잭션을 두지 않는다)
 * ① 좌석 확보(RegisterGatheringMemberUseCase, 자기 트랜잭션): 소진·마감이면 여기서 실패해 결제 기록도 만들지 않는다.
 * ② PENDING 결제 기록 선저장(자기 트랜잭션): paymentKey를 승인 전에 durable하게 남긴다.
 * ③ PG 최종 승인(PaymentGatewayPort.confirm, 트랜잭션 밖).
 * ④ 성공이면 APPROVED로 전이(좌석은 PENDING 유지), 실패면 FAILED로 전이 후 좌석 복원(보상)하고 402.
 */
```

`complete` 본문의 ① 이후(register 결과 이후) 전체를 다음으로 교체:

```kotlin
		// ② PENDING 결제 기록 선저장 (자기 트랜잭션). paymentKey를 승인 전에 durable하게 남긴다.
		val payment: Payment = savePaymentPort.save(
			Payment(
				userId = userId,
				gatheringId = product.gatheringId,
				scheduleId = product.scheduleId,
				productId = command.productId,
				gender = gender,
				amount = registered.amount,
				paymentKey = command.paymentKey,
				status = PaymentStatus.PENDING,
			),
		)

		// ③ PG 최종 승인 (트랜잭션 밖).
		val approved: Boolean = paymentGatewayPort.confirm(command.paymentKey, registered.amount)
		if (!approved) {
			// ④-실패: 기록을 FAILED로 남기고(이력 보존) 좌석 복원 후 402.
			updatePaymentStatusPort.updateStatus(payment.id!!, PaymentStatus.FAILED)
			releaseGatheringSeatUseCase.release(product.scheduleId, userId)
			throw BusinessException(PaymentsErrorCode.PAYMENT_CONFIRM_FAILED)
		}

		// ④-성공: 기록을 APPROVED로 전이. 좌석은 PENDING 유지(어드민 승인 존치).
		updatePaymentStatusPort.updateStatus(payment.id!!, PaymentStatus.APPROVED)
		return CompletePaymentResult(amount = registered.amount)
	}
```

(교체 대상은 기존 `// ② PG 최종 승인 ...`부터 `return CompletePaymentResult(...)`까지. ① register 블록은 그대로 둔다.)

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew :meeple-api:test --tests "com.org.meeple.api.payments.PaymentsCompleteE2ETest" -q`
Expected: PASS (성공=APPROVED, confirm-fail=FAILED 기록·좌석 복원 모두 만족).

- [ ] **Step 5: 커밋**

```bash
git add meeple-core meeple-api
git commit -m "feat(payments): PG 승인 전 PENDING 결제기록 선저장·승인 결과로 상태 전이"
```

---

## 검증(전체)

- [ ] `./gradlew :meeple-api:test --tests "com.org.meeple.api.payments.*" -q` — 체크아웃·결제완료 E2E 전부 PASS.

## 배포 전 게이트(범위 밖, 기억용)

- payments.sql(status·uk_payment_key) 운영 DB 반영(ddl-auto=validate).
- 실 PG 어댑터로 `UnconfiguredPaymentGatewayAdapter` 교체(confirm이 예외를 던지면 ④ 분기 미도달 → PENDING 잔류·release 미호출: 예외→보상 매핑 필요).
- PENDING 잔류 대사(reconciliation) 배치, payment_key 유니크 위반의 우아한 재생 처리는 후속 과제.
