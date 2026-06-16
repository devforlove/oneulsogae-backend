package com.org.meeple.chatting.chat.application.port.out

import java.time.LocalDateTime

/**
 * 채팅방 갱신 아웃포트. (chatting 소유, infra 어댑터가 구현)
 * 방을 도메인으로 로드하지 않고, 활성 상태 검증과 마지막 메세지 갱신을 **조건부 UPDATE 한 번**으로 처리한다. (전송 경로의 SELECT 제거)
 */
interface UpdateChatRoomPort {

	/**
	 * 활성(ACTIVE) 상태인 방에 한해 마지막 메세지/수신 시각을 갱신한다.
	 * 갱신된 행이 있으면 true. 없으면(방이 없거나 종료/만료됨) false를 반환한다. (종료 검증을 원자적으로 겸한다)
	 */
	fun updateLastMessageIfActive(chatRoomId: Long, lastMessage: String, lastMessageAt: LocalDateTime): Boolean
}
