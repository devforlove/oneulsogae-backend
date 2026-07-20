package com.org.meeple.infra.lounge.query

import com.org.meeple.common.lounge.LoungePostType
import com.org.meeple.core.lounge.query.dao.GetSelfIntroPostDao
import com.org.meeple.core.lounge.query.dto.SelfIntroPostView
import com.org.meeple.infra.lounge.command.entity.QLoungePostEntity
import com.org.meeple.infra.lounge.command.entity.QLoungePostImageEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetSelfIntroPostDao]의 QueryDSL 구현. (조회 전용)
 * 엔티티를 거치지 않고 [SelfIntroPostView] read model로 바로 투영한다. imageKey까지만 담고 imageUrl은 서비스가 presign으로 채운다.
 * 표시용 작성자 닉네임은 user_details를, 대표 사진은 노출 순서 0번 사진을 각각 left join으로 붙인다.
 * (프로필이나 사진이 없어도 글은 보여야 하므로 inner join하지 않는다)
 * type 동등 + id 내림차순 keyset(`id < :beforeId`)이 `idx_type_id`로 받쳐져 뒤 페이지에서도 seek로 끝난다(offset 스캔 없음).
 */
@Component
class GetSelfIntroPostDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetSelfIntroPostDao {

	override fun findPage(beforeId: Long?, limit: Int): List<SelfIntroPostView> {
		val post: QLoungePostEntity = QLoungePostEntity.loungePostEntity
		val userDetail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		val image: QLoungePostImageEntity = QLoungePostImageEntity.loungePostImageEntity
		return queryFactory
			.select(
				Projections.constructor(
					SelfIntroPostView::class.java,
					post.id,
					userDetail.nickname,
					post.likeCount,
					image.imageKey,
				),
			)
			.from(post)
			.leftJoin(userDetail).on(userDetail.userId.eq(post.userId))
			.leftJoin(image).on(image.postId.eq(post.id).and(image.displayOrder.eq(FIRST_PHOTO_ORDER)))
			.where(
				post.type.eq(LoungePostType.SELF_INTRO),
				beforeId?.let { cursor: Long -> post.id.lt(cursor) },
			)
			.orderBy(post.id.desc())
			.limit(limit.toLong())
			.fetch()
	}

	companion object {
		/** 그리드 타일에 쓰는 대표 사진의 노출 순서. */
		private const val FIRST_PHOTO_ORDER: Int = 0
	}
}
