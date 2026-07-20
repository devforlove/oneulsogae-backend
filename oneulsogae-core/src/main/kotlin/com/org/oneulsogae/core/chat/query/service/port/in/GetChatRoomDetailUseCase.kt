package com.org.oneulsogae.core.chat.query.service.port.`in`

import com.org.oneulsogae.core.chat.query.dto.ChatRoomDetail

/**
 * 채팅방 상세 조회 인포트(유스케이스).
 * 특정 채팅방의 참여자 정보와 채팅 메세지를 함께 조회한다. (조회자는 그 채팅방의 참가자여야 한다)
 */
interface GetChatRoomDetailUseCase {

	/**
	 * [chatRoomId] 채팅방의 참여자 정보와 메세지를 조회한다.
	 * 메세지는 최근 [size]건을 id 오름차순(과거→최신)으로 담아 내려주며, [beforeMessageId]가 있으면 그 id보다 과거 구간을 잇는다. (키셋 페이지네이션)
	 * [userId]가 그 채팅방 참가자가 아니면 예외를 던진다.
	 */
	fun getChatRoomDetail(userId: Long, chatRoomId: Long, beforeMessageId: Long?, size: Int): ChatRoomDetail
}
