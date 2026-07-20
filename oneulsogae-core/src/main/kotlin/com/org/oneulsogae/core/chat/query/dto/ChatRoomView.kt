package com.org.oneulsogae.core.chat.query.dto

import com.org.oneulsogae.common.chat.ChatRoomMatchType
import com.org.oneulsogae.common.chat.ChatRoomStatus

/**
 * 채팅방 상세 첫 페이지 헤더용 read model. (방의 식별·공통 상태만 담는 투영)
 * 상세 조회는 방의 상태만 필요하므로 명령 도메인([com.org.oneulsogae.core.chat.command.domain.ChatRoom]) 대신 이 read model을 쓴다.
 * (query는 command의 out-port·도메인에 의존하지 않고 자기 dao만 본다)
 */
data class ChatRoomView(
	val chatRoomId: Long,
	/** 채팅방 종류(SOLO 1:1 / TEAM 2:2). */
	val matchType: ChatRoomMatchType,
	val status: ChatRoomStatus,
)
