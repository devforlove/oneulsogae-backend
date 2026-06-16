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

	fun countByChatRoomId(chatRoomId: Long): Long

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

	/**
	 * 주어진 방들의 참가자와 프로필을 조회한다. (채팅방 목록 표시용)
	 * **나간(소프트 삭제된) 참가자도 포함**해야 그 사람의 프로필(닉네임·프로필이미지)을 계속 보여줄 수 있으므로,
	 * 의도적으로 네이티브 쿼리를 써서 chat_room_members의 @SQLRestriction(deleted_at is null)을 적용하지 않는다.
	 * 단 user_details 프로필은 유효한 것만 쓰도록 `d.deleted_at is null`을 명시한다. (네이티브라 자동 적용되지 않음)
	 * 정렬(chat_room_id, user_id)은 복합 유니크 인덱스 순서와 동일하다.
	 */
	@Query(
		value = """
		select m.chat_room_id      as chatRoomId,
		       m.user_id            as userId,
		       d.nickname           as nickname,
		       d.profile_image_code as profileImageCode,
		       d.gender             as gender
		from chat_room_members m
		join user_details d on d.user_id = m.user_id and d.deleted_at is null
		where m.chat_room_id in (:roomIds)
		order by m.chat_room_id asc, m.user_id asc
		""",
		nativeQuery = true,
	)
	fun findPartnerParticipants(@Param("roomIds") roomIds: List<Long>): List<PartnerParticipantRow>
}

/**
 * [ChatRoomMemberJpaRepository.findPartnerParticipants] 네이티브 조회 결과 프로젝션.
 * gender는 enum이 varchar(@Enumerated.STRING)로 저장돼 있어 네이티브에선 문자열로 내려오므로, 매핑은 호출 측(어댑터)에서 [com.org.meeple.common.user.Gender]로 변환한다.
 */
interface PartnerParticipantRow {
	val chatRoomId: Long
	val userId: Long
	val nickname: String?
	val profileImageCode: String?
	val gender: String?
}
