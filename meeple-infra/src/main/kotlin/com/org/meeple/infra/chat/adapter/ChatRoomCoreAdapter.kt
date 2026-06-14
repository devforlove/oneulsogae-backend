package com.org.meeple.infra.chat.adapter

import com.org.meeple.core.chat.application.port.out.SaveChatRoomPort
import com.org.meeple.core.chat.domain.ChatRoom
import com.org.meeple.infra.chat.mapper.toDomain
import com.org.meeple.infra.chat.mapper.toEntity
import com.org.meeple.infra.chat.repository.ChatRoomJpaRepository
import org.springframework.stereotype.Component

/**
 * core 모듈이 쓰는 [ChatRoomEntity]의 Spring Data 어댑터.
 * 저장([SaveChatRoomPort])을 `ChatRoomJpaRepository`로 구현한다.
 * QueryDSL이 필요한 조회는 [ChatRoomQueryCoreAdapter]가 별도로 둔다.
 */
@Component
class ChatRoomCoreAdapter(
	private val chatRoomJpaRepository: ChatRoomJpaRepository,
) : SaveChatRoomPort {

	// id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge). 둘 다 Spring Data save가 처리한다.
	override fun save(chatRoom: ChatRoom): ChatRoom =
		chatRoomJpaRepository.save(chatRoom.toEntity()).toDomain()
}
