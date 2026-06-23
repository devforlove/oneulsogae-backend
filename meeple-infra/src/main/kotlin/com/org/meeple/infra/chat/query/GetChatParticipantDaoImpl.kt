package com.org.meeple.infra.chat.query

import com.org.meeple.common.chat.ChatRoomMemberStatus
import com.org.meeple.core.chat.query.dao.GetChatParticipantDao
import com.org.meeple.core.chat.query.dto.ChatParticipant
import com.org.meeple.core.chat.query.dto.ChatParticipants
import com.org.meeple.infra.chat.command.entity.QChatRoomMemberEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetChatParticipantDao]의 QueryDSL 구현체.
 * 참가자 프로필 조인 조회만 전담하며, `JPAQueryFactory`만 주입한다. (조회 전용)
 */
@Component
class GetChatParticipantDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetChatParticipantDao {

	/**
	 * 한 채팅방의 참가자를 프로필(닉네임·이미지·성별)과 함께 조회한다. (나간(DEACTIVE) 참가자도 포함 — 나가도 프로필은 노출)
	 * 이 결과는 상세 첫 페이지의 참가자 노출과 접근 검증([ChatParticipants.validateParticipant])에 함께 쓰이며, 나감 여부로 거르지 않는다. (목록/상세 공통 정책)
	 * WHERE는 복합 유니크 인덱스 ux_chat_room_id_user_id의 선두 컬럼 동등(chat_room_id)이라 인덱스 레인지 스캔을 탄다.
	 * 정렬(user_id)은 그 인덱스의 두 번째 컬럼이라 chat_room_id 동등 뒤로 인덱스 순서와 일치해 추가 정렬이 없다.
	 * 프로필 누락으로 참가 검증이 깨지지 않도록 left join으로 둔다. (참가자 행이 있으면 프로필 유무와 무관하게 결과에 남는다)
	 */
	override fun findByChatRoomId(chatRoomId: Long): ChatParticipants {
		val chatRoomMember: QChatRoomMemberEntity = QChatRoomMemberEntity.chatRoomMemberEntity
		val userDetail: QUserDetailEntity = QUserDetailEntity.userDetailEntity

		return ChatParticipants(
			queryFactory
				.select(
					Projections.constructor(
						ChatParticipant::class.java,
						chatRoomMember.userId,
						userDetail.nickname,
						userDetail.profileImageCode,
						userDetail.gender,
						chatRoomMember.lastReadMessageId,
						chatRoomMember.status.eq(ChatRoomMemberStatus.ACTIVE),
					),
				)
				.from(chatRoomMember)
				.leftJoin(userDetail).on(userDetail.userId.eq(chatRoomMember.userId))
				.where(
					chatRoomMember.chatRoomId.eq(chatRoomId),
				)
				.orderBy(chatRoomMember.userId.asc())
				.fetch(),
		)
	}
}
