package com.org.meeple.core.chat.command.application.port.`in`

import com.org.meeple.core.chat.command.application.port.`in`.result.ChatRoomMatch

/**
 * 채팅방이 어느 매칭에서 생성됐는지([ChatRoomMatch])를 조회하는 인포트.
 * 신고처럼 채팅방 맥락에서 상대(매칭 종류+id)를 알아야 하는 다른 도메인이 in-port로 호출한다.
 */
interface GetChatRoomMatchUseCase {

	/** [chatRoomId] 채팅방의 매칭 정보(matchType+matchId)를 반환한다. 채팅방이 없으면 예외를 던진다. */
	fun getMatch(chatRoomId: Long): ChatRoomMatch
}
