package com.org.oneulsogae.infra.chat.query

import com.org.oneulsogae.common.chat.ChatRoomMemberStatus
import com.org.oneulsogae.common.chat.ChatRoomStatus
import com.org.oneulsogae.core.chat.query.dao.GetChatRoomDao
import com.org.oneulsogae.core.chat.query.dto.ChatParticipant
import com.org.oneulsogae.core.chat.query.dto.ChatRoomSummary
import com.org.oneulsogae.core.chat.query.dto.ChatRoomView
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomMemberEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.querydsl.core.group.GroupBy
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetChatRoomDao]의 QueryDSL 구현체.
 * 동적 컬럼·조인이 필요한 채팅방 목록 조회를 전담한다. (조회 전용 — out-port는 [ChatRoomAdapter]가 메서드 쿼리로 따로 구현)
 * 나가기가 status(DEACTIVE) 전이로 바뀌어 참가자 행을 더는 소프트 삭제하지 않으므로, @SQLRestriction을 우회할 네이티브 쿼리가 필요 없어 `JPAQueryFactory`만 주입한다.
 */
@Component
class GetChatRoomDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetChatRoomDao {

	/**
	 * 사용자가 참가한 ACTIVE 채팅방을 상대 참가자들의 프로필·사용자 관점의 안 읽은 개수와 함께 [ChatRoomSummary]로 반환한다.
	 * 그룹챗(방당 참가자 N명)에서 상대 참가자를 한 쿼리로 조인하면 방이 (상대 수)만큼 중복행으로 펼쳐지므로, 두 단계로 나눠 방당 한 건으로 모은다.
	 * 1) 내 참가자 행(user_id = :userId)에서 출발해(→ idx_user_id) 내가 (활성으로) 참가한 ACTIVE 방과 내 안 읽은 개수를 최근 대화 순으로 조회한다.
	 * 2) 그 방들(chat_room_id in ...)의 참가자와 프로필을 한 번에 모아, 방별로 묶어 1)의 순서를 유지하며 채운다. (1+N 방지)
	 *    참가자 목록에는 나간(DEACTIVE) 참가자도 프로필 노출을 위해 포함한다. (소프트삭제만 제외, 조회자(나)는 where에서 제외)
	 * (@SQLRestriction으로 관련 엔티티의 soft delete 행은 자동 제외된다)
	 */
	override fun findActiveByUserId(userId: Long): List<ChatRoomSummary> {
		val rooms: List<ChatRoomSummary> = findActiveRooms(userId)
		if (rooms.isEmpty()) {
			return emptyList()
		}

		val participantsByRoom: Map<Long, List<ChatParticipant>> =
			findPartnerParticipants(rooms.map { it.chatRoomId }, userId)

		return rooms.map { room: ChatRoomSummary ->
			room.copy(participants = participantsByRoom[room.chatRoomId].orEmpty())
		}
	}

	// 채팅방 식별·상태 단건 조회. (상세 첫 페이지 헤더용) 없으면 null. (@SQLRestriction으로 소프트삭제 방은 제외)
	override fun findById(chatRoomId: Long): ChatRoomView? {
		val chatRoom: QChatRoomEntity = QChatRoomEntity.chatRoomEntity
		return queryFactory
			.select(
				Projections.constructor(
					ChatRoomView::class.java,
					chatRoom.id,
					chatRoom.matchType,
					chatRoom.status,
				),
			)
			.from(chatRoom)
			.where(chatRoom.id.eq(chatRoomId))
			.fetchOne()
	}

	/**
	 * 1단계: 내가 (활성으로) 참가한 ACTIVE 방 + 내 안 읽은 개수를 최근 대화 순(마지막 메세지 시각 → id 내림차순)으로 [ChatRoomSummary]에 바로 투영한다. (내가 나간(DEACTIVE) 방은 status로 제외)
	 * 참가자 목록은 한 쿼리로 합칠 수 없어(2단계 조회) 빈 리스트 상수로 채워두고, 2단계에서 [ChatRoomSummary.copy]로 채운다.
	 */
	private fun findActiveRooms(userId: Long): List<ChatRoomSummary> {
		val chatRoom: QChatRoomEntity = QChatRoomEntity.chatRoomEntity
		val chatRoomMember: QChatRoomMemberEntity = QChatRoomMemberEntity.chatRoomMemberEntity

		return queryFactory
			.select(
				Projections.constructor(
					ChatRoomSummary::class.java,
					chatRoom.id,
					Expressions.constant(emptyList<ChatParticipant>()),
					chatRoom.matchType,
					chatRoom.status,
					chatRoom.expiredAt,
					chatRoomMember.unreadCount,
					chatRoom.lastMessage,
					chatRoom.lastMessageAt,
				),
			)
			.from(chatRoomMember)
			.join(chatRoom).on(chatRoom.id.eq(chatRoomMember.chatRoomId))
			.where(
				chatRoomMember.userId.eq(userId),
				chatRoomMember.status.eq(ChatRoomMemberStatus.ACTIVE),
				chatRoom.status.eq(ChatRoomStatus.ACTIVE),
			)
			.orderBy(chatRoom.lastMessageAt.desc(), chatRoom.id.desc())
			.fetch()
	}

	/**
	 * 2단계: 주어진 방들의 참가자와 프로필을 조회해, 중간 행 객체 없이 QueryDSL [GroupBy.transform]으로 방별 [ChatParticipant] 목록에 바로 투영한다.
	 * 그룹 키(chat_room_id)는 [ChatParticipant]에 담지 않고 묶음 키로만 쓰며, 참가자 행마다 [ChatParticipant]를 바로 만든다.
	 * 나간(DEACTIVE) 참가자도 프로필 노출을 위해 status로 거르지 않는다. (나가기가 소프트 삭제가 아니라 @SQLRestriction이 이들을 빼지 않으므로 네이티브 없이 충분하다)
	 * 프로필(user_details)은 @SQLRestriction으로 soft delete된 행이 자동 제외되고, gender는 enum 그대로 투영된다. 조회자(나)는 where에서 제외한다.
	 * TEAM 방에서는 내 행(me)을 self-join해 상대 팀만(partner.team_id != me.team_id) 남긴다. SOLO는 me.team_id가 null이라 전원 유지.
	 */
	private fun findPartnerParticipants(roomIds: List<Long>, userId: Long): Map<Long, List<ChatParticipant>> {
		val partner: QChatRoomMemberEntity = QChatRoomMemberEntity("partner")
		val me: QChatRoomMemberEntity = QChatRoomMemberEntity("me")
		val userDetail: QUserDetailEntity = QUserDetailEntity.userDetailEntity

		return queryFactory
			.from(partner)
			.join(me).on(me.chatRoomId.eq(partner.chatRoomId).and(me.userId.eq(userId)))
			.join(userDetail).on(userDetail.userId.eq(partner.userId))
			.where(
				partner.chatRoomId.`in`(roomIds),
				partner.userId.ne(userId),
				me.teamId.isNull.or(partner.teamId.ne(me.teamId)),
			)
			.orderBy(partner.chatRoomId.asc(), partner.userId.asc())
			.transform(
				GroupBy.groupBy(partner.chatRoomId).`as`(
					GroupBy.list(
						Projections.constructor(
							ChatParticipant::class.java,
							partner.userId,
							userDetail.nickname,
							userDetail.profileImageCode,
							userDetail.gender,
							partner.lastReadMessageId,
							partner.status.eq(ChatRoomMemberStatus.ACTIVE),
							// 목록은 상대 팀만 내려주므로(위 where에서 내 팀 제외) isMyTeam은 항상 false.
							Expressions.constant(false),
						),
					),
				),
			)
	}
}
