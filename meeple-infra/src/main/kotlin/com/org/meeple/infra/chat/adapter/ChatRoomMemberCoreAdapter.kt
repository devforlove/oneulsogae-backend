package com.org.meeple.infra.chat.adapter

import com.org.meeple.core.chat.application.port.out.GetChatRoomMemberPort
import com.org.meeple.core.chat.application.port.out.SaveChatRoomMemberPort
import com.org.meeple.core.chat.domain.ChatRoomMember
import com.org.meeple.core.chat.domain.ChatRoomMembers
import com.org.meeple.infra.chat.entity.ChatRoomMemberEntity
import com.org.meeple.infra.chat.mapper.toDomain
import com.org.meeple.infra.chat.mapper.toEntity
import com.org.meeple.infra.chat.repository.ChatRoomMemberJpaRepository
import org.springframework.stereotype.Component

/**
 * core 모듈이 쓰는 [ChatRoomMemberEntity]의 Spring Data 어댑터.
 * 단순 존재 조회([GetChatRoomMemberPort])와 참가자 저장([SaveChatRoomMemberPort])을 `ChatRoomMemberJpaRepository`로 구현한다.
 * 조회 조건이 파생 쿼리로 표현되므로 QueryDSL을 쓰지 않는다. 프로필 조인 조회는 QueryDSL 어댑터([ChatRoomMemberQueryCoreAdapter])가 따로 담당한다.
 */
@Component
class ChatRoomMemberCoreAdapter(
	private val chatRoomMemberJpaRepository: ChatRoomMemberJpaRepository,
) : GetChatRoomMemberPort, SaveChatRoomMemberPort {

	override fun existsByChatRoomIdAndUserId(chatRoomId: Long, userId: Long): Boolean =
		chatRoomMemberJpaRepository.existsByChatRoomIdAndUserId(chatRoomId, userId)

	// id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge). 둘 다 Spring Data save가 처리한다.
	override fun save(member: ChatRoomMember): ChatRoomMember =
		chatRoomMemberJpaRepository.save(member.toEntity()).toDomain()

	override fun saveAll(members: ChatRoomMembers): ChatRoomMembers {
		val entities: List<ChatRoomMemberEntity> = members.values.map { it.toEntity() }
		return ChatRoomMembers(
			chatRoomMemberJpaRepository.saveAll(entities).map { it.toDomain() },
		)
	}
}
