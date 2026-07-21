package com.org.oneulsogae.api.lounge.response

import com.org.oneulsogae.core.lounge.command.application.port.`in`.result.RequestLoungeChatResult

/** 대화 신청 응답. [requestId]는 작성자가 수락할 때 쓰는 키다. */
data class LoungeChatRequestResponse(
	val requestId: Long,
) {
	companion object {

		fun of(result: RequestLoungeChatResult): LoungeChatRequestResponse =
			LoungeChatRequestResponse(requestId = result.requestId)
	}
}
