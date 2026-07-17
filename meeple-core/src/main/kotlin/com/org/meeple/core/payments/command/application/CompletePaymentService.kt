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
import com.org.meeple.core.payments.command.application.port.out.PaymentGatewayPort
import com.org.meeple.core.payments.command.application.port.out.SavePaymentPort
import com.org.meeple.core.payments.command.domain.Payment
import com.org.meeple.core.user.query.service.port.`in`.GetUserDetailUseCase
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
