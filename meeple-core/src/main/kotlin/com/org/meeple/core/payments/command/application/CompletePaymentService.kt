package com.org.meeple.core.payments.command.application

import com.org.meeple.common.user.Gender
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.gathering.command.application.port.`in`.RegisterGatheringMemberUseCase
import com.org.meeple.core.gathering.command.application.port.`in`.command.RegisterGatheringMemberCommand
import com.org.meeple.core.gathering.command.application.port.`in`.result.RegisterGatheringMemberResult
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
 * 본인 프로필 성별을 확정하고(요청으로 받지 않음 — 체크아웃에서 보류한 성별 강제),
 * gathering in-port로 참가를 승인대기 등록한 뒤 서버 확정가로 결제 기록을 남긴다.
 * 참가 등록과 결제 기록은 같은 트랜잭션이다(둘 중 하나만 남지 않는다).
 */
@Service
@Transactional
class CompletePaymentService(
	private val getUserDetailUseCase: GetUserDetailUseCase,
	private val registerGatheringMemberUseCase: RegisterGatheringMemberUseCase,
	private val savePaymentPort: SavePaymentPort,
) : CompletePaymentUseCase {

	override fun complete(userId: Long, command: CompletePaymentCommand): CompletePaymentResult {
		val gender: Gender = getUserDetailUseCase.getByUserId(userId).gender
			?: throw BusinessException(PaymentsErrorCode.ORDERER_GENDER_REQUIRED)

		val registered: RegisterGatheringMemberResult = registerGatheringMemberUseCase.register(
			RegisterGatheringMemberCommand(
				gatheringId = command.gatheringId,
				scheduleId = command.scheduleId,
				userId = userId,
				gender = gender,
			),
		)

		savePaymentPort.save(
			Payment(
				userId = userId,
				gatheringId = command.gatheringId,
				scheduleId = command.scheduleId,
				gender = gender,
				amount = registered.amount,
			),
		)
		return CompletePaymentResult(amount = registered.amount)
	}
}
