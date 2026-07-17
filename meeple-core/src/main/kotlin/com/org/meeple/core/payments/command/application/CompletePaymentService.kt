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
				type = product.type,
			),
		)

		savePaymentPort.save(
			Payment(
				userId = userId,
				gatheringId = product.gatheringId,
				scheduleId = product.scheduleId,
				// 가격 근거: 요청이 지정한 상품 id를 그대로 남긴다.
				productId = command.productId,
				gender = gender,
				amount = registered.amount,
				paymentKey = command.paymentKey,
			),
		)
		return CompletePaymentResult(amount = registered.amount)
	}
}
