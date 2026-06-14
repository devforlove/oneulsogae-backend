package com.org.meeple.infra.chat.adapter

import com.org.meeple.core.chat.application.port.out.GetChatParticipantPort
import com.org.meeple.core.chat.domain.ChatParticipant
import com.org.meeple.core.chat.domain.ChatParticipants
import com.org.meeple.infra.chat.entity.QChatRoomMemberEntity
import com.org.meeple.infra.user.entity.QUserDetailEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * core 모듈이 쓰는 [com.org.meeple.infra.chat.entity.ChatRoomMemberEntity]의 QueryDSL 어댑터.
 * 참가자 프로필 조인 조회([GetChatParticipantPort])만 전담하며, `JPAQueryFactory`만 주입한다.
 * 단건 조회·저장은 [ChatRoomMemberCoreAdapter](Spring Data)가 따로 둔다.
 */
@Component
class ChatRoomMemberQueryCoreAdapter(
	private val queryFactory: JPAQueryFactory,
) : GetChatParticipantPort {

	/**
	 * 한 채팅방의 참가자를 프로필(닉네임·이미지·성별)과 함께 조회한다. (나간 참가자 포함)
	 * WHERE를 복합 유니크 인덱스 udx_chat_room_id_user_id의 선두 컬럼 동등(chat_room_id) 하나로만 두어 인덱스 레인지 스캔을 타게 한다.
	 * 정렬(user_id)은 그 인덱스의 두 번째 컬럼이라 chat_room_id 동등 뒤로 인덱스 순서와 일치해 추가 정렬이 없다. (member 측 컬럼은 인덱스만으로 커버)
	 * 프로필 누락으로 참가 검증이 깨지지 않도록 left join으로 둔다. (참가자 행이 있으면 프로필 유무와 무관하게 결과에 남는다)
	 */
	override fun findByChatRoomId(chatRoomId: Long): ChatParticipants {
		val member: QChatRoomMemberEntity = QChatRoomMemberEntity.chatRoomMemberEntity
		val detail: QUserDetailEntity = QUserDetailEntity.userDetailEntity

		return ChatParticipants(
			queryFactory
				.select(
					Projections.constructor(
						ChatParticipant::class.java,
						member.userId,
						detail.nickname,
						detail.profileImageCode,
						detail.gender,
					),
				)
				.from(member)
				.leftJoin(detail).on(detail.userId.eq(member.userId))
				.where(member.chatRoomId.eq(chatRoomId))
				.orderBy(member.userId.asc())
				.fetch(),
		)
	}
}
