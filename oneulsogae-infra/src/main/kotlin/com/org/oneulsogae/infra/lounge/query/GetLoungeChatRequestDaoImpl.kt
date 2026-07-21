package com.org.oneulsogae.infra.lounge.query

import com.org.oneulsogae.common.chat.ChatRoomMatchType
import com.org.oneulsogae.core.lounge.query.dao.GetLoungeChatRequestDao
import com.org.oneulsogae.core.lounge.query.dto.LoungeChatRequestView
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungeChatRequestEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungePostEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetLoungeChatRequestDao]의 QueryDSL 구현. (조회 전용)
 * 엔티티를 거치지 않고 [LoungeChatRequestView] read model로 바로 투영한다. 만 나이는 서비스가 birthday로 채운다.
 * 신청자 프로필(user_details)은 프로필이 없어도 신청은 보여야 하므로 left join한다.
 * 채팅방은 수락 전에는 없으므로 left join하며, `(match_type, match_id)` 유니크 인덱스로 seek한다.
 * post_id 동등 + id 내림차순 keyset(`id < :beforeId`)이 `idx_post_id_id`로 받쳐져 뒤 페이지에서도 seek로 끝난다(offset 스캔 없음).
 */
@Component
class GetLoungeChatRequestDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetLoungeChatRequestDao {

	override fun findAuthorUserIdByPostId(postId: Long): Long? {
		val post: QLoungePostEntity = QLoungePostEntity.loungePostEntity
		return queryFactory
			.select(post.userId)
			.from(post)
			.where(post.id.eq(postId))
			.fetchFirst()
	}

	override fun findPageByPostId(postId: Long, beforeId: Long?, limit: Int): List<LoungeChatRequestView> {
		val request: QLoungeChatRequestEntity = QLoungeChatRequestEntity.loungeChatRequestEntity
		val userDetail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		val chatRoom: QChatRoomEntity = QChatRoomEntity.chatRoomEntity
		return queryFactory
			.select(
				Projections.constructor(
					LoungeChatRequestView::class.java,
					request.id,
					request.requesterUserId,
					userDetail.nickname,
					userDetail.gender,
					userDetail.birthday,
					request.status,
					chatRoom.id,
					request.createdAt,
				),
			)
			.from(request)
			.leftJoin(userDetail).on(userDetail.userId.eq(request.requesterUserId))
			.leftJoin(chatRoom).on(
				chatRoom.matchType.eq(ChatRoomMatchType.LOUNGE).and(chatRoom.matchId.eq(request.id)),
			)
			.where(
				request.postId.eq(postId),
				beforeId?.let { cursor: Long -> request.id.lt(cursor) },
			)
			.orderBy(request.id.desc())
			.limit(limit.toLong())
			.fetch()
	}
}
