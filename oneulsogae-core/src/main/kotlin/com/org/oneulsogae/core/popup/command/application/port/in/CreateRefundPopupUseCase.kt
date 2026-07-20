package com.org.oneulsogae.core.popup.command.application.port.`in`

/** 매칭 실패 환불 안내 개인 팝업 생성 인포트(유스케이스). */
interface CreateRefundPopupUseCase {

	/** [userId]에게 [refundAmount]코인 환불을 알리는 개인 팝업을 생성한다. */
	fun createMatchFailedRefund(userId: Long, refundAmount: Int)

	/** [userId]에게 [refundAmount]코인 환불을 알리는 미팅(팀) 매칭 실패 개인 팝업을 생성한다. */
	fun createMeetingFailedRefund(userId: Long, refundAmount: Int)
}
