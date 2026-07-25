package com.org.oneulsogae.infra.lounge.query

import com.org.oneulsogae.core.lounge.query.dao.GetLoungeCommentDao
import com.org.oneulsogae.core.lounge.query.dto.LoungeCommentView
import com.org.oneulsogae.infra.lounge.command.entity.QLoungeCommentEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.querydsl.core.types.Expression
import com.querydsl.core.types.Projections
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetLoungeCommentDao]의 QueryDSL 구현. (조회 전용)
 * 엔티티를 거치지 않고 [LoungeCommentView] read model로 바로 투영한다.
 * 표시용 작성자 프로필은 user_details를 left join으로 붙인다. (프로필이 없어도 댓글은 보여야 한다)
 * 댓글 엔티티에는 @SQLRestriction이 없으므로 삭제 행 제외를 여기서 조건으로 직접 거른다 —
 * root는 살아있는 대댓글이 남아 있으면 삭제돼도 포함하고("삭제된 댓글" 표시용), 대댓글은 삭제 행을 뺀다.
 * post_id 동등 + parent_id is null + id 오름차순 keyset(`id > :afterId`)이 `idx_post_id_parent_id_id`로 받쳐진다.
 */
@Component
class GetLoungeCommentDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetLoungeCommentDao {

	override fun findRootPage(postId: Long, afterId: Long?, limit: Int): List<LoungeCommentView> {
		val comment: QLoungeCommentEntity = QLoungeCommentEntity.loungeCommentEntity
		val reply: QLoungeCommentEntity = QLoungeCommentEntity("reply")
		val userDetail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		return queryFactory
			.select(projection(comment, userDetail))
			.from(comment)
			.leftJoin(userDetail).on(userDetail.userId.eq(comment.userId))
			.where(
				comment.postId.eq(postId),
				comment.parentId.isNull,
				afterId?.let { cursor: Long -> comment.id.gt(cursor) },
				// 삭제된 root는 살아있는 대댓글이 남아 있을 때만 포함한다. (대댓글까지 없으면 목록에서 뺀다)
				comment.deletedAt.isNull.or(
					JPAExpressions.selectOne()
						.from(reply)
						.where(reply.parentId.eq(comment.id), reply.deletedAt.isNull)
						.exists(),
				),
			)
			.orderBy(comment.id.asc())
			.limit(limit.toLong())
			.fetch()
	}

	override fun findRepliesByParentIds(parentCommentIds: List<Long>): List<LoungeCommentView> {
		val comment: QLoungeCommentEntity = QLoungeCommentEntity.loungeCommentEntity
		val userDetail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		return queryFactory
			.select(projection(comment, userDetail))
			.from(comment)
			.leftJoin(userDetail).on(userDetail.userId.eq(comment.userId))
			.where(
				comment.parentId.`in`(parentCommentIds),
				comment.deletedAt.isNull,
			)
			.orderBy(comment.id.asc())
			.fetch()
	}

	/** 댓글 행 + 작성자 프로필을 [LoungeCommentView]로 투영하는 공통 프로젝션. */
	private fun projection(
		comment: QLoungeCommentEntity,
		userDetail: QUserDetailEntity,
	): Expression<LoungeCommentView> =
		Projections.constructor(
			LoungeCommentView::class.java,
			comment.id,
			comment.parentId,
			comment.content,
			comment.deletedAt.isNotNull,
			comment.createdAt,
			comment.userId,
			userDetail.nickname,
			userDetail.profileImageCode,
		)
}
