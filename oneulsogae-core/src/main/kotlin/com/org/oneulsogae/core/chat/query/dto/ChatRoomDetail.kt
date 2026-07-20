package com.org.oneulsogae.core.chat.query.dto

import com.org.oneulsogae.common.chat.ChatRoomMatchType
import com.org.oneulsogae.common.chat.ChatRoomStatus

/**
 * 채팅방 상세 조회 결과(read model).
 * 메세지([messages])는 id 오름차순(과거→최신) 순으로 담기며, 더 과거를 이어 읽을 커서는 [ChatMessageViews.nextCursor]로 제공한다.
 * [matchType]·[status]·[participants]는 첫 페이지(beforeMessageId == null)에서만 채워진다. 이후 커서 페이지에서는 클라이언트가 이미 보유하므로 다시 싣지 않는다.
 * (커서 페이지에서는 [matchType]·[status]는 null, [participants]는 빈 목록이다)
 */
data class ChatRoomDetail(
	val chatRoomId: Long,
	val matchType: ChatRoomMatchType?,
	val status: ChatRoomStatus?,
	val participants: List<ChatParticipant>,
	val messages: ChatMessageViews,
)
