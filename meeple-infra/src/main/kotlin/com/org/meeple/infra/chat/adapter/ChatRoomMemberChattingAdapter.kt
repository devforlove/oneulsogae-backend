package com.org.meeple.infra.chat.adapter

import com.org.meeple.chatting.chat.application.port.out.GetChatRoomMemberPort
import com.org.meeple.chatting.chat.application.port.out.IncreaseUnreadCountPort
import com.org.meeple.infra.chat.repository.ChatRoomMemberJpaRepository
import org.springframework.stereotype.Component

/**
 * chatting 모듈이 쓰는 [com.org.meeple.infra.chat.entity.ChatRoomMemberEntity]의 Spring Data 어댑터.
 * 발신자 존재 검증([GetChatRoomMemberPort])과 안 읽은 개수 벌크 증가([IncreaseUnreadCountPort])를 구현한다. (참가자를 로드하지 않는다)
 * 같은 엔티티의 core 포트는 [ChatRoomMemberCoreAdapter]가 따로 구현한다.
 */
@Component
class ChatRoomMemberChattingAdapter(
	private val chatRoomMemberJpaRepository: ChatRoomMemberJpaRepository,
) : GetChatRoomMemberPort, IncreaseUnreadCountPort {

	override fun existsByChatRoomIdAndUserId(chatRoomId: Long, userId: Long): Boolean =
		chatRoomMemberJpaRepository.existsByChatRoomIdAndUserId(chatRoomId, userId)

	override fun increaseForOthers(chatRoomId: Long, senderId: Long) {
		chatRoomMemberJpaRepository.increaseUnreadCountForOthers(chatRoomId, senderId)
	}
}
