package com.org.oneulsogae.core.lounge.command.application.port.`in`.result

/** 대화 신청 수락 결과. 생성된 채팅방의 id를 돌려준다. (클라이언트가 바로 채팅방으로 이동하는 키) */
data class AcceptLoungeChatResult(
	val chatRoomId: Long,
)
