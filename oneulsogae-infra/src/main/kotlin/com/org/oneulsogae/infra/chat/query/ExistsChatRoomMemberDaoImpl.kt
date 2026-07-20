package com.org.oneulsogae.infra.chat.query

import com.org.oneulsogae.common.chat.ChatRoomMemberStatus
import com.org.oneulsogae.core.chat.query.dao.ExistsChatRoomMemberDao
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomMemberEntity
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [ExistsChatRoomMemberDao]의 QueryDSL 구현체.
 * 접근 검증용 단건 존재 조회만 전담한다. (조회 전용)
 * 같은 (chat_room_id, user_id) 단건 조회라도 변경 대상 로드용 out-port([com.org.oneulsogae.core.chat.command.application.port.out.GetChatRoomMemberPort])는
 * [ChatRoomMemberAdapter]가 메서드 쿼리로 따로 구현한다. (adapter=메서드 쿼리 / dao=QueryDSL 분리)
 */
@Component
class ExistsChatRoomMemberDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : ExistsChatRoomMemberDao {

	/**
	 * [userId]가 [chatRoomId] 채팅방의 활성(ACTIVE) 참가자인지 존재 여부만 확인한다. (chat_room_id, user_id) 복합 유니크 인덱스로 단건 확인한다.
	 * 나간(DEACTIVE) 참가자는 status로 제외한다. (비참가자로 취급해 접근 검증에서 거른다)
	 */
	override fun existsByChatRoomIdAndUserId(chatRoomId: Long, userId: Long): Boolean {
		val chatRoomMember: QChatRoomMemberEntity = QChatRoomMemberEntity.chatRoomMemberEntity
		return queryFactory
			.selectOne()
			.from(chatRoomMember)
			.where(
				chatRoomMember.chatRoomId.eq(chatRoomId),
				chatRoomMember.userId.eq(userId),
				chatRoomMember.status.eq(ChatRoomMemberStatus.ACTIVE),
			)
			.fetchFirst() != null
	}
}
