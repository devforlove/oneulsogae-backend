package com.org.oneulsogae.core.lounge.command.application.port.`in`

import com.org.oneulsogae.core.lounge.command.application.port.`in`.result.RequestLoungeChatResult

/**
 * 라운지 셀소 글 작성자에게 대화를 신청하는 인포트(유스케이스).
 * 신청 비용(코인)은 서버가 산출해 차감한다.
 */
interface RequestLoungeChatUseCase {

	fun request(userId: Long, postId: Long): RequestLoungeChatResult
}
