package com.org.meeple.core.popup.command.application

import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.popup.command.application.port.`in`.CreateRefundPopupUseCase
import com.org.meeple.core.popup.command.application.port.out.SavePopupPort
import com.org.meeple.core.popup.command.domain.Popup
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [CreateRefundPopupUseCase] 구현. (명령 경로)
 * 환불 사실을 알리는 개인 팝업([Popup.matchFailedRefund], [Popup.meetingFailedRefund])을 만들어 저장한다.
 */
@Service
@Transactional
class CreateRefundPopupService(
	private val savePopupPort: SavePopupPort,
	private val timeGenerator: TimeGenerator,
) : CreateRefundPopupUseCase {

	override fun createMatchFailedRefund(userId: Long, refundAmount: Int) {
		val now: LocalDateTime = timeGenerator.now()
		savePopupPort.save(Popup.matchFailedRefund(userId = userId, refundAmount = refundAmount, now = now))
	}

	override fun createMeetingFailedRefund(userId: Long, refundAmount: Int) {
		val now: LocalDateTime = timeGenerator.now()
		savePopupPort.save(Popup.meetingFailedRefund(userId = userId, refundAmount = refundAmount, now = now))
	}
}
