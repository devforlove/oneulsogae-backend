package com.org.meeple.infra.chat.command.adapter

import com.org.meeple.chatting.chat.application.port.out.GetChatRoomMemberPort as ChattingGetChatRoomMemberPort
import com.org.meeple.chatting.chat.application.port.out.IncreaseUnreadCountPort
import com.org.meeple.core.chat.command.domain.ChatRoomMember
import com.org.meeple.core.chat.command.domain.ChatRoomMembers
import com.org.meeple.core.chat.command.service.port.out.GetChatRoomMemberPort
import com.org.meeple.core.chat.command.service.port.out.SaveChatRoomMemberPort
import com.org.meeple.infra.chat.command.entity.ChatRoomMemberEntity
import com.org.meeple.infra.chat.command.mapper.toDomain
import com.org.meeple.infra.chat.command.mapper.toEntity
import com.org.meeple.infra.chat.command.repository.ChatRoomMemberJpaRepository
import org.springframework.stereotype.Component

/**
 * [ChatRoomMemberEntity]의 out-port 어댑터. (Spring Data 메서드 쿼리)
 * 같은 엔티티를 쓰는 core·chatting 모듈의 out-port를 한 어댑터에서 함께 구현한다.
 * - core: 변경 대상 로드·종료 판정([GetChatRoomMemberPort]) + 참가자 저장([SaveChatRoomMemberPort]).
 * - chatting: 발신자 존재 검증([ChattingGetChatRoomMemberPort]) + 안 읽은 개수 벌크 증가([IncreaseUnreadCountPort]).
 * 접근 검증용 존재 조회·프로필 조인 조회는 query 쪽 QueryDSL 구현체([ChatRoomMemberQueryDaoImpl], [ChatParticipantQueryDaoImpl])가 따로 담당한다.
 */
@Component
class ChatRoomMemberAdapter(
	private val chatRoomMemberJpaRepository: ChatRoomMemberJpaRepository,
) : GetChatRoomMemberPort, SaveChatRoomMemberPort, ChattingGetChatRoomMemberPort, IncreaseUnreadCountPort {

	override fun findByChatRoomIdAndUserId(chatRoomId: Long, userId: Long): ChatRoomMember? =
		chatRoomMemberJpaRepository.findByChatRoomIdAndUserId(chatRoomId, userId)?.toDomain()

	override fun countByChatRoomId(chatRoomId: Long): Long =
		chatRoomMemberJpaRepository.countByChatRoomId(chatRoomId)

	// id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge). 둘 다 Spring Data save가 처리한다.
	override fun save(member: ChatRoomMember): ChatRoomMember =
		chatRoomMemberJpaRepository.save(member.toEntity()).toDomain()

	override fun saveAll(members: ChatRoomMembers): ChatRoomMembers {
		val entities: List<ChatRoomMemberEntity> = members.values.map { it.toEntity() }
		return ChatRoomMembers(
			chatRoomMemberJpaRepository.saveAll(entities).map { it.toDomain() },
		)
	}

	// chatting(발송 경로): 발신자가 그 방의 참가자인지 존재 검증.
	override fun existsByChatRoomIdAndUserId(chatRoomId: Long, userId: Long): Boolean =
		chatRoomMemberJpaRepository.existsByChatRoomIdAndUserId(chatRoomId, userId)

	// chatting(발송 경로): 발신자를 제외한 참가자들의 안 읽은 개수를 한 번의 UPDATE로 +1.
	override fun increaseForOthers(chatRoomId: Long, senderId: Long) {
		chatRoomMemberJpaRepository.increaseUnreadCountForOthers(chatRoomId, senderId)
	}
}
