package com.org.meeple.infra.chat.query

import com.org.meeple.core.chat.query.dao.ChatRoomMemberQueryDao
import com.org.meeple.infra.chat.command.entity.QChatRoomMemberEntity
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [ChatRoomMemberQueryDao]의 QueryDSL 구현체.
 * 접근 검증용 단건 존재 조회만 전담한다. (조회 전용)
 * 같은 (chat_room_id, user_id) 단건 조회라도 변경 대상 로드용 out-port([com.org.meeple.core.chat.command.application.port.out.GetChatRoomMemberPort])는
 * [ChatRoomMemberAdapter]가 메서드 쿼리로 따로 구현한다. (adapter=메서드 쿼리 / dao=QueryDSL 분리)
 */
@Component
class ChatRoomMemberQueryDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : ChatRoomMemberQueryDao {

	/**
	 * [userId]가 [chatRoomId] 채팅방의 참가자인지 존재 여부만 확인한다. (chat_room_id, user_id) 복합 유니크 인덱스로 단건 확인한다.
	 * @SQLRestriction이 적용돼 소프트 삭제(나가기)된 참가자 행은 제외된다. (나감(exitedAt) 여부는 보지 않는다)
	 */
	override fun existsByChatRoomIdAndUserId(chatRoomId: Long, userId: Long): Boolean {
		val member: QChatRoomMemberEntity = QChatRoomMemberEntity.chatRoomMemberEntity
		return queryFactory
			.selectOne()
			.from(member)
			.where(
				member.chatRoomId.eq(chatRoomId),
				member.userId.eq(userId),
			)
			.fetchFirst() != null
	}
}
