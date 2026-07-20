package com.org.oneulsogae.chatting.chat.application.port.`in`.command

import com.org.oneulsogae.common.chat.ChatMessageType

/**
 * 채팅 메세지 발송 입력.
 * 어느 방([chatRoomId])에 누가([senderId]) 무슨 본문([content])을 어떤 유형([type])으로 보내는지 받는다.
 * 보낸 시각/식별자는 도메인·서버가 채운다. ([senderId]는 컨트롤러가 인증 Principal에서 채운다)
 * [type]이 SYSTEM이면 보낸이 없이 시스템 안내 메세지로 저장한다. ([senderId]는 발행 권한(활성 참가자) 검증에만 쓰고 메세지엔 담지 않는다)
 */
data class SendChatMessageCommand(
	val chatRoomId: Long,
	val senderId: Long,
	val content: String,
	val type: ChatMessageType = ChatMessageType.USER,
)
