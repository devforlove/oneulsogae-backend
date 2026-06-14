package com.org.meeple.infra.chat.adapter

import com.org.meeple.common.chat.ChatRoomStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.core.chat.application.port.out.GetChatRoomPort
import com.org.meeple.core.chat.domain.ChatParticipant
import com.org.meeple.core.chat.domain.ChatRoom
import com.org.meeple.core.chat.domain.ChatRoomSummary
import com.org.meeple.infra.chat.entity.QChatRoomEntity
import com.org.meeple.infra.chat.entity.QChatRoomMemberEntity
import com.org.meeple.infra.chat.mapper.toDomain
import com.org.meeple.infra.user.entity.QUserDetailEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * core 모듈이 쓰는 [ChatRoomEntity]의 QueryDSL 어댑터.
 * 동적 컬럼·조인이 필요한 조회([GetChatRoomPort])만 전담하며, `JPAQueryFactory`만 주입한다.
 * 저장은 [ChatRoomCoreAdapter]가 별도로 둔다.
 */
@Component
class ChatRoomQueryCoreAdapter(
	private val queryFactory: JPAQueryFactory,
) : GetChatRoomPort {

	// id 단건 조회. 상세 조회의 참가자 검증·참가자 식별에 쓰며, 엔티티를 도메인 모델로 변환해 반환한다.
	override fun findById(chatRoomId: Long): ChatRoom? {
		val chatRoom: QChatRoomEntity = QChatRoomEntity.chatRoomEntity
		return queryFactory
			.selectFrom(chatRoom)
			.where(chatRoom.id.eq(chatRoomId))
			.fetchOne()
			?.toDomain()
	}

	// 매칭 id 단건 조회. (match_id 유니크라 단건) 멱등 생성에서 기존 방 존재 확인에 쓴다.
	override fun findByMatchId(matchId: Long): ChatRoom? {
		val chatRoom: QChatRoomEntity = QChatRoomEntity.chatRoomEntity
		return queryFactory
			.selectFrom(chatRoom)
			.where(chatRoom.matchId.eq(matchId))
			.fetchOne()
			?.toDomain()
	}

	/**
	 * 사용자가 참가한 ACTIVE 채팅방을 상대 참가자들의 프로필·사용자 관점의 안 읽은 개수와 함께 [ChatRoomSummary]로 반환한다.
	 * 그룹챗(방당 참가자 N명)에서 상대 참가자를 한 쿼리로 조인하면 방이 (상대 수)만큼 중복행으로 펼쳐지므로, 두 단계로 나눠 방당 한 건으로 모은다.
	 * 1) 내 참가자 행(user_id = :userId)에서 출발해(→ idx_user_id) 내가 참가한 ACTIVE 방과 내 안 읽은 개수를 최근 대화 순으로 조회한다.
	 * 2) 그 방들(chat_room_id in ...)의 참가자와 프로필을 한 번에 모아, 방별로 묶어 1)의 순서를 유지하며 채운다. (1+N 방지)
	 *    참가자 목록에는 나간 참가자도 포함하며, 조회자(나) 제외만 애플리케이션에서 처리한다. (인덱스를 못 타는 조건을 SQL에서 빼기 위함)
	 * (@SQLRestriction으로 관련 엔티티의 soft delete 행은 자동 제외된다)
	 */
	override fun findActiveByUserId(userId: Long): List<ChatRoomSummary> {
		val rooms: List<ActiveRoomRow> = findActiveRoomRows(userId)
		if (rooms.isEmpty()) {
			return emptyList()
		}

		val participantsByRoom: Map<Long, List<ChatParticipant>> =
			findPartnerParticipants(rooms.map { it.chatRoomId }, userId)

		return rooms.map { room: ActiveRoomRow ->
			ChatRoomSummary(
				chatRoomId = room.chatRoomId,
				participants = participantsByRoom[room.chatRoomId].orEmpty(),
				status = room.status,
				expiredAt = room.expiredAt,
				unreadCount = room.unreadCount,
				lastMessage = room.lastMessage,
				lastMessageAt = room.lastMessageAt,
			)
		}
	}

	// 1단계: 내가 참가한 ACTIVE 방 + 내 안 읽은 개수를 최근 대화 순(마지막 메세지 시각 → id 내림차순)으로 조회한다. (나감 여부로 거르지 않는다)
	private fun findActiveRoomRows(userId: Long): List<ActiveRoomRow> {
		val chatRoom: QChatRoomEntity = QChatRoomEntity.chatRoomEntity
		val myMember: QChatRoomMemberEntity = QChatRoomMemberEntity.chatRoomMemberEntity

		return queryFactory
			.select(
				Projections.constructor(
					ActiveRoomRow::class.java,
					chatRoom.id,
					chatRoom.status,
					chatRoom.expiredAt,
					myMember.unreadCount,
					chatRoom.lastMessage,
					chatRoom.lastMessageAt,
				),
			)
			.from(myMember)
			.join(chatRoom).on(chatRoom.id.eq(myMember.chatRoomId))
			.where(
				myMember.userId.eq(userId),
				chatRoom.status.eq(ChatRoomStatus.ACTIVE),
			)
			.orderBy(chatRoom.lastMessageAt.desc(), chatRoom.id.desc())
			.fetch()
	}

	/**
	 * 2단계: 주어진 방들의 참가자와 프로필을 모아 방별로 묶는다.
	 * WHERE를 복합 유니크 인덱스 udx_chat_room_id_user_id의 선두 컬럼 IN(chat_room_id) 하나로만 두어 인덱스 레인지 스캔만 타게 한다.
	 * - 시크에 못 쓰는 부등호(user_id <> :userId)는 SQL에서 빼고, 조회자(나) 제외는 애플리케이션에서 [filter]한다.
	 * - exited_at은 인덱스가 없어 잔여 필터가 되는 데다 나간 참가자도 목록에 포함하므로 조건에서 뺀다.
	 * - 정렬(chat_room_id, user_id)은 그 인덱스 순서와 동일해 추가 정렬 비용을 최소화한다. (member 측은 인덱스만으로 커버)
	 */
	private fun findPartnerParticipants(roomIds: List<Long>, userId: Long): Map<Long, List<ChatParticipant>> {
		val partnerMember: QChatRoomMemberEntity = QChatRoomMemberEntity.chatRoomMemberEntity
		val partnerDetail: QUserDetailEntity = QUserDetailEntity.userDetailEntity

		return queryFactory
			.select(
				Projections.constructor(
					PartnerRow::class.java,
					partnerMember.chatRoomId,
					partnerMember.userId,
					partnerDetail.nickname,
					partnerDetail.profileImageCode,
					partnerDetail.gender,
				),
			)
			.from(partnerMember)
			.join(partnerDetail).on(partnerDetail.userId.eq(partnerMember.userId))
			.where(partnerMember.chatRoomId.`in`(roomIds))
			.orderBy(partnerMember.chatRoomId.asc(), partnerMember.userId.asc())
			.fetch()
			.filter { it.userId != userId }
			.groupBy(
				{ it.chatRoomId },
				{ ChatParticipant(userId = it.userId, nickname = it.nickname, profileImageCode = it.profileImageCode, gender = it.gender) },
			)
	}

	// 1단계 프로젝션 홀더: 방 공통 상태 + 조회 사용자 관점의 안 읽은 개수. (참가자 목록은 2단계에서 채운다)
	data class ActiveRoomRow(
		val chatRoomId: Long,
		val status: ChatRoomStatus,
		val expiredAt: LocalDateTime,
		val unreadCount: Int,
		val lastMessage: String?,
		val lastMessageAt: LocalDateTime?,
	)

	// 2단계 프로젝션 홀더: 어느 방의 어떤 상대 참가자(프로필·성별 포함)인지. (chatRoomId로 묶어 ChatParticipant로 변환한다)
	data class PartnerRow(
		val chatRoomId: Long,
		val userId: Long,
		val nickname: String?,
		val profileImageCode: String?,
		val gender: Gender?,
	)
}
