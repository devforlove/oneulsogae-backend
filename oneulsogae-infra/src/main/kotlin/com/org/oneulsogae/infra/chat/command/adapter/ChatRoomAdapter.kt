package com.org.oneulsogae.infra.chat.command.adapter

import com.org.oneulsogae.chatting.chat.application.port.out.UpdateChatRoomPort
import com.org.oneulsogae.common.chat.ChatRoomMatchType
import com.org.oneulsogae.core.chat.command.domain.ChatRoom
import com.org.oneulsogae.core.chat.command.application.port.out.GetChatRoomPort
import com.org.oneulsogae.core.chat.command.application.port.out.SaveChatRoomPort
import com.org.oneulsogae.infra.chat.command.mapper.toDomain
import com.org.oneulsogae.infra.chat.command.mapper.toEntity
import com.org.oneulsogae.infra.chat.command.repository.ChatRoomJpaRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * [com.org.oneulsogae.infra.chat.command.entity.ChatRoomEntity]의 out-port 어댑터. (Spring Data 메서드 쿼리)
 * 같은 엔티티를 쓰는 core·chatting 모듈의 out-port를 한 어댑터에서 함께 구현한다.
 * - core: 저장([SaveChatRoomPort]) + 명령 흐름의 단건 로드([GetChatRoomPort]).
 * - chatting: 발송 경로의 조건부 갱신([UpdateChatRoomPort], 방을 로드하지 않는다).
 * 목록(read model) 조회는 query 쪽 QueryDSL 구현체([GetChatRoomDaoImpl])가 따로 담당한다. (adapter=메서드 쿼리 / dao=QueryDSL 분리)
 */
@Component
class ChatRoomAdapter(
	private val chatRoomJpaRepository: ChatRoomJpaRepository,
) : SaveChatRoomPort, GetChatRoomPort, UpdateChatRoomPort {

	// id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge). 둘 다 Spring Data save가 처리한다.
	override fun save(chatRoom: ChatRoom): ChatRoom =
		chatRoomJpaRepository.save(chatRoom.toEntity()).toDomain()

	// id 단건 조회. (참가자 검증·상세 조회용)
	override fun findById(chatRoomId: Long): ChatRoom? =
		chatRoomJpaRepository.findById(chatRoomId).orElse(null)?.toDomain()

	// 변경 흐름에서 방 행을 비관적 쓰기 락(FOR UPDATE)으로 로드한다. (방 락을 가장 먼저 잡아 동시 변경 직렬화 → 데드락 방지)
	override fun findByIdForUpdate(chatRoomId: Long): ChatRoom? =
		chatRoomJpaRepository.findByIdForUpdate(chatRoomId)?.toDomain()

	// 매칭 타입+id 단건 조회. ((match_type, match_id) 유니크라 단건) 멱등 생성에서 기존 방 존재 확인에 쓴다.
	override fun findByMatchTypeAndMatchId(matchType: ChatRoomMatchType, matchId: Long): ChatRoom? =
		chatRoomJpaRepository.findByMatchTypeAndMatchId(matchType, matchId)?.toDomain()

	// 발송 경로(chatting): ACTIVE인 방만 마지막 메세지/시각을 조건부 UPDATE. (방을 로드하지 않는다. 이 UPDATE가 방 X락을 잡아 발송의 락 게이트도 겸한다)
	override fun updateLastMessageIfActive(chatRoomId: Long, lastMessage: String, lastMessageAt: LocalDateTime): Boolean =
		chatRoomJpaRepository.updateLastMessageIfActive(chatRoomId, lastMessage, lastMessageAt) > 0
}
