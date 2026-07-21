package com.org.oneulsogae.api.lounge.response

import com.org.oneulsogae.core.lounge.command.application.port.`in`.result.AcceptLoungeChatResult

/** 대화 신청 수락 응답. [chatRoomId]로 바로 채팅방에 진입한다. */
data class AcceptLoungeChatResponse(
	val chatRoomId: Long,
) {
	companion object {

		fun of(result: AcceptLoungeChatResult): AcceptLoungeChatResponse =
			AcceptLoungeChatResponse(chatRoomId = result.chatRoomId)
	}
}
