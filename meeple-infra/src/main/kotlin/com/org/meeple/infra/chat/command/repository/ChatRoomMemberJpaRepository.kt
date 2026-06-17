package com.org.meeple.infra.chat.command.repository

import com.org.meeple.common.chat.ChatRoomMemberStatus
import com.org.meeple.infra.chat.command.entity.ChatRoomMemberEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * 채팅방 참가자 영속성 엔티티에 대한 Spring Data JPA 리포지토리.
 * 활성 참가자 존재·수 조회와 (status 무관) 단건 조회 같은 파생 쿼리와, 발송 경로의 안 읽은 개수 벌크 증가([increaseUnreadCountForOthers])를 담당한다.
 * 나간 참가자는 행을 지우지 않고 status=DEACTIVE로 두므로, 활성 판정 쿼리는 status로 거른다. (프로필 노출이 필요한 목록 조회는 status로 거르지 않는다)
 * 프로필 조인 조회는 별도 Query 어댑터([com.org.meeple.infra.chat.query.GetChatParticipantDaoImpl])에 있다.
 */
interface ChatRoomMemberJpaRepository : JpaRepository<ChatRoomMemberEntity, Long> {

	fun existsByChatRoomIdAndUserIdAndStatus(chatRoomId: Long, userId: Long, status: ChatRoomMemberStatus): Boolean

	fun findByChatRoomIdAndUserId(chatRoomId: Long, userId: Long): ChatRoomMemberEntity?

	/** 한 채팅방의 참가자 전체. (소프트삭제 행은 @SQLRestriction으로 제외) 방 종료 시 일괄 소프트 삭제용. */
	fun findByChatRoomId(chatRoomId: Long): List<ChatRoomMemberEntity>

	fun countByChatRoomIdAndStatus(chatRoomId: Long, status: ChatRoomMemberStatus): Long

	/**
	 * [chatRoomId] 방에서 [senderId]를 제외한 활성 참가자들의 안 읽은 개수를 1 증가시킨다. 갱신된 행 수를 반환한다.
	 * 참가자 전체를 로드해 건별 save 하지 않고 한 번의 UPDATE로 처리한다. (인원수 무관)
	 * 벌크 JPQL UPDATE는 @SQLRestriction이 자동 적용되지 않으므로 `deleted_at` 조건을 명시하고, 나간(DEACTIVE) 참가자는 status로 제외한다.
	 */
	@Modifying
	@Query(
		"""
		update ChatRoomMemberEntity m
		set m.unreadCount = m.unreadCount + 1
		where m.chatRoomId = :chatRoomId
		  and m.userId <> :senderId
		  and m.status = :status
		  and m.deletedAt is null
		""",
	)
	fun increaseUnreadCountForOthers(
		@Param("chatRoomId") chatRoomId: Long,
		@Param("senderId") senderId: Long,
		@Param("status") status: ChatRoomMemberStatus,
	): Int
}
