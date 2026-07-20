package com.org.meeple.core.payments.command.application

import com.org.meeple.common.user.Gender
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.gathering.command.application.port.`in`.RegisterGatheringMemberUseCase
import com.org.meeple.core.gathering.command.application.port.`in`.ReleaseGatheringSeatUseCase
import com.org.meeple.core.gathering.command.application.port.`in`.command.RegisterGatheringMemberCommand
import com.org.meeple.core.gathering.command.application.port.`in`.result.RegisterGatheringMemberResult
import com.org.meeple.core.gathering.query.dto.GatheringProductIdentity
import com.org.meeple.core.gathering.query.service.port.`in`.GetGatheringsUseCase
import com.org.meeple.core.payments.PaymentsErrorCode
import com.org.meeple.core.payments.command.application.port.`in`.CompletePaymentUseCase
import com.org.meeple.core.payments.command.application.port.`in`.command.CompletePaymentCommand
import com.org.meeple.core.payments.command.application.port.`in`.result.CompletePaymentResult
import com.org.meeple.core.payments.command.application.port.out.PaymentConfirmResult
import com.org.meeple.core.payments.command.application.port.out.PaymentGatewayPort
import com.org.meeple.core.payments.command.application.port.out.SaveGatheringPaymentPort
import com.org.meeple.core.payments.command.application.port.out.UpdateGatheringPaymentStatusPort
import com.org.meeple.core.payments.command.domain.GatheringPayment
import com.org.meeple.core.payments.command.domain.PaymentStatus
import com.org.meeple.core.user.query.service.port.`in`.GetUserDetailUseCase
import org.springframework.stereotype.Service

/**
 * [CompletePaymentUseCase] 구현. (오케스트레이터 — 외부 호출을 포함해 클래스 트랜잭션을 두지 않는다)
 * ① 좌석 확보(RegisterGatheringMemberUseCase, 자기 트랜잭션): 소진·마감이면 여기서 실패해 결제 기록도 만들지 않는다.
 * ② PENDING 결제 기록 선저장(자기 트랜잭션): paymentKey를 승인 전에 durable하게 남긴다.
 * ③ PG 최종 승인(PaymentGatewayPort.confirm, 트랜잭션 밖).
 * ④ 성공이면 APPROVED로 전이(좌석은 PENDING 유지), 실패면 FAILED로 전이 후 좌석 복원(보상)하고 402.
 */
@Service
class CompletePaymentService(
	private val getUserDetailUseCase: GetUserDetailUseCase,
	private val getGatheringsUseCase: GetGatheringsUseCase,
	private val registerGatheringMemberUseCase: RegisterGatheringMemberUseCase,
	private val releaseGatheringSeatUseCase: ReleaseGatheringSeatUseCase,
	private val paymentGatewayPort: PaymentGatewayPort,
	private val saveGatheringPaymentPort: SaveGatheringPaymentPort,
	private val updateGatheringPaymentStatusPort: UpdateGatheringPaymentStatusPort,
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

		// ② PENDING 결제 기록 선저장 (자기 트랜잭션). paymentKey를 승인 전에 durable하게 남긴다.
		val payment: GatheringPayment = saveGatheringPaymentPort.save(
			GatheringPayment(
				userId = userId,
				gatheringId = product.gatheringId,
				scheduleId = product.scheduleId,
				productId = command.productId,
				gender = gender,
				amount = registered.amount,
				paymentKey = command.paymentKey,
				orderId = command.orderId,
				status = PaymentStatus.PENDING,
			),
		)

		// ③ PG 최종 승인 (트랜잭션 밖).
		val confirmed: PaymentConfirmResult = paymentGatewayPort.confirm(command.paymentKey, command.orderId, registered.amount)
		if (!confirmed.approved) {
			// ④-실패: 기록을 FAILED로 남기고(사유·이력 보존) 좌석 복원 후 402.
			updateGatheringPaymentStatusPort.updateStatus(payment.id!!, PaymentStatus.FAILED, confirmed.failReason)
			releaseGatheringSeatUseCase.release(product.scheduleId, userId)
			throw BusinessException(PaymentsErrorCode.PAYMENT_CONFIRM_FAILED)
		}

		// ④-성공: 기록을 APPROVED로 전이. 좌석은 PENDING 유지(어드민 승인 존치).
		updateGatheringPaymentStatusPort.updateStatus(payment.id!!, PaymentStatus.APPROVED)
		return CompletePaymentResult(amount = registered.amount)
	}
}
