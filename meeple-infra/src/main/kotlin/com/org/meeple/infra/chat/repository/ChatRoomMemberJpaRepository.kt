package com.org.meeple.infra.chat.repository

import com.org.meeple.infra.chat.entity.ChatRoomMemberEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * 채팅방 참가자 영속성 엔티티에 대한 Spring Data JPA 리포지토리.
 * 단순 존재 조회(파생 쿼리)와, 발송 경로의 안 읽은 개수 벌크 증가([increaseUnreadCountForOthers])를 담당한다.
 * 프로필 조인 조회는 별도 Query 어댑터([com.org.meeple.infra.chat.adapter.ChatRoomMemberQueryCoreAdapter])에 있다.
 */
interface ChatRoomMemberJpaRepository : JpaRepository<ChatRoomMemberEntity, Long> {

	fun existsByChatRoomIdAndUserId(chatRoomId: Long, userId: Long): Boolean

	fun findByChatRoomIdAndUserId(chatRoomId: Long, userId: Long): ChatRoomMemberEntity?

	/**
	 * [chatRoomId] 방에서 [senderId]를 제외한 참가자들의 안 읽은 개수를 1 증가시킨다. 갱신된 행 수를 반환한다.
	 * 참가자 전체를 로드해 건별 save 하지 않고 한 번의 UPDATE로 처리한다. (인원수 무관)
	 * 벌크 JPQL UPDATE는 @SQLRestriction이 자동 적용되지 않으므로 `deleted_at` 조건을 명시한다.
	 */
	@Modifying
	@Query(
		"""
		update ChatRoomMemberEntity m
		set m.unreadCount = m.unreadCount + 1
		where m.chatRoomId = :chatRoomId
		  and m.userId <> :senderId
		  and m.deletedAt is null
		""",
	)
	fun increaseUnreadCountForOthers(
		@Param("chatRoomId") chatRoomId: Long,
		@Param("senderId") senderId: Long,
	): Int
}
