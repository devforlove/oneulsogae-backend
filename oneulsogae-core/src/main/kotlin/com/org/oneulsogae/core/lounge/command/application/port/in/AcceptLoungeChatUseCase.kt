package com.org.oneulsogae.core.lounge.command.application.port.`in`

import com.org.oneulsogae.core.lounge.command.application.port.`in`.result.AcceptLoungeChatResult

/**
 * 내 셀소에 온 대화 신청을 수락하는 인포트(유스케이스).
 * 수락 비용(코인)이 차감되고 신청자와의 채팅방이 열린다.
 */
interface AcceptLoungeChatUseCase {

	fun accept(userId: Long, requestId: Long): AcceptLoungeChatResult
}
