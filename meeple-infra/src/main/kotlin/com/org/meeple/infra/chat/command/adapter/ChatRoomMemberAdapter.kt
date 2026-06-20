package com.org.meeple.infra.chat.command.adapter

import com.org.meeple.chatting.chat.application.port.out.AdvanceReadPointerPort
import com.org.meeple.chatting.chat.application.port.out.GetChatRoomMemberPort as ChattingGetChatRoomMemberPort
import com.org.meeple.chatting.chat.application.port.out.IncreaseUnreadCountPort
import java.time.LocalDateTime
import com.org.meeple.core.chat.command.domain.ChatRoomMember
import com.org.meeple.core.chat.command.domain.ChatRoomMembers
import com.org.meeple.common.chat.ChatRoomMemberStatus
import com.org.meeple.core.chat.command.application.port.out.GetChatRoomMemberPort
import com.org.meeple.core.chat.command.application.port.out.SaveChatRoomMemberPort
import com.org.meeple.infra.chat.command.entity.ChatRoomMemberEntity
import com.org.meeple.infra.chat.command.mapper.toDomain
import com.org.meeple.infra.chat.command.mapper.toEntity
import com.org.meeple.infra.chat.command.repository.ChatRoomMemberJpaRepository
import org.springframework.stereotype.Component

/**
 * [ChatRoomMemberEntity]의 out-port 어댑터. (Spring Data 메서드 쿼리)
 * 같은 엔티티를 쓰는 core·chatting 모듈의 out-port를 한 어댑터에서 함께 구현한다.
 * - core: 변경 대상 로드·종료 판정([GetChatRoomMemberPort]) + 참가자 저장([SaveChatRoomMemberPort]).
 * - chatting: 발신자 존재 검증([ChattingGetChatRoomMemberPort]) + 안 읽은 개수 벌크 증가([IncreaseUnreadCountPort]) + 읽음 포인터 전진([AdvanceReadPointerPort]).
 * 접근 검증용 존재 조회·프로필 조인 조회는 query 쪽 QueryDSL 구현체([ExistsChatRoomMemberDaoImpl], [GetChatParticipantDaoImpl])가 따로 담당한다.
 */
@Component
class ChatRoomMemberAdapter(
	private val chatRoomMemberJpaRepository: ChatRoomMemberJpaRepository,
) : GetChatRoomMemberPort, SaveChatRoomMemberPort, ChattingGetChatRoomMemberPort, IncreaseUnreadCountPort, AdvanceReadPointerPort {

	// 참가자 행을 status와 무관하게 로드한다. ((chat_room_id, user_id) 유니크라 최대 한 건)
	override fun findByChatRoomIdAndUserId(chatRoomId: Long, userId: Long): ChatRoomMember? =
		chatRoomMemberJpaRepository.findByChatRoomIdAndUserId(chatRoomId, userId)?.toDomain()

	// 방의 참가자 전체를 로드한다. (소프트삭제 제외) 방 종료 시 일괄 소프트 삭제용.
	override fun findAllByChatRoomId(chatRoomId: Long): ChatRoomMembers =
		ChatRoomMembers(chatRoomMemberJpaRepository.findByChatRoomId(chatRoomId).map { it.toDomain() })

	override fun countActiveByChatRoomId(chatRoomId: Long): Long =
		chatRoomMemberJpaRepository.countByChatRoomIdAndStatus(chatRoomId, ChatRoomMemberStatus.ACTIVE)

	// id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge). 둘 다 Spring Data save가 처리한다.
	override fun save(member: ChatRoomMember): ChatRoomMember =
		chatRoomMemberJpaRepository.save(member.toEntity()).toDomain()

	override fun saveAll(members: ChatRoomMembers): ChatRoomMembers {
		val entities: List<ChatRoomMemberEntity> = members.values.map { it.toEntity() }
		return ChatRoomMembers(
			chatRoomMemberJpaRepository.saveAll(entities).map { it.toDomain() },
		)
	}

	// chatting(발송 경로): 발신자가 그 방의 활성 참가자인지 존재 검증. (나간(DEACTIVE) 사용자는 발송 불가)
	override fun existsByChatRoomIdAndUserId(chatRoomId: Long, userId: Long): Boolean =
		chatRoomMemberJpaRepository.existsByChatRoomIdAndUserIdAndStatus(chatRoomId, userId, ChatRoomMemberStatus.ACTIVE)

	// chatting(발송 경로): 발신자를 제외한 활성 참가자들의 안 읽은 개수를 한 번의 UPDATE로 +1.
	override fun increaseForOthers(chatRoomId: Long, senderId: Long) {
		chatRoomMemberJpaRepository.increaseUnreadCountForOthers(chatRoomId, senderId, ChatRoomMemberStatus.ACTIVE)
	}

	// chatting(읽음 경로): 한 참가자의 읽음 포인터를 forward-only로 전진시키고 뱃지를 리셋한다. (갱신 행 수 반환)
	override fun advance(chatRoomId: Long, userId: Long, lastReadMessageId: Long, now: LocalDateTime): Int =
		chatRoomMemberJpaRepository.advanceReadPointer(chatRoomId, userId, lastReadMessageId, now, ChatRoomMemberStatus.ACTIVE)
}
