package com.org.oneulsogae.infra.lounge.query

import com.org.oneulsogae.common.lounge.LoungePostType
import com.org.oneulsogae.core.lounge.query.dao.GetSelfIntroPostDao
import com.org.oneulsogae.core.lounge.query.dto.SelfIntroPostDetailView
import com.org.oneulsogae.core.lounge.query.dto.SelfIntroPostView
import com.org.oneulsogae.infra.lounge.command.entity.QLoungePostEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungePostImageEntity
import com.org.oneulsogae.infra.lounge.command.entity.QSelfIntroPostEntity
import com.org.oneulsogae.infra.region.entity.QRegionEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetSelfIntroPostDao]의 QueryDSL 구현. (조회 전용)
 * 엔티티를 거치지 않고 [SelfIntroPostView] read model로 바로 투영한다. imageKey까지만 담고 imageUrl은 서비스가 presign으로 채운다.
 * 표시용 작성자 닉네임은 user_details를, 대표 사진은 노출 순서 0번 사진을 각각 left join으로 붙인다.
 * (프로필이나 사진이 없어도 글은 보여야 하므로 inner join하지 않는다)
 * type 동등 + id 내림차순 keyset(`id < :beforeId`)이 `idx_type_id`로 받쳐져 뒤 페이지에서도 seek로 끝난다(offset 스캔 없음).
 * 상세는 PK 동등 조건으로 단건 투영하며 본문(self_intro_posts)을 inner join한다(없으면 null → 서비스가 404).
 * 사진은 상세와 분리해 노출 순서대로 따로 읽는다(한 글에 여러 장이라 조인하면 상세 행이 사진 수만큼 곱해진다).
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

	override fun findDetailByPostId(postId: Long): SelfIntroPostDetailView? {
		val post: QLoungePostEntity = QLoungePostEntity.loungePostEntity
		val selfIntro: QSelfIntroPostEntity = QSelfIntroPostEntity.selfIntroPostEntity
		val userDetail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		val region: QRegionEntity = QRegionEntity.regionEntity
		return queryFactory
			.select(
				Projections.constructor(
					SelfIntroPostDetailView::class.java,
					post.id,
					userDetail.nickname,
					post.likeCount,
					userDetail.gender,
					userDetail.birthday,
					userDetail.height,
					// 표시용 활동지역은 regions를 join해 "시/도 시/군/구"로 만든다. (지역 미설정이면 null)
					region.sido.concat(" ").concat(region.sigungu),
					userDetail.job,
					selfIntro.longDistance,
					selfIntro.desiredAge,
					selfIntro.mbti,
					selfIntro.marriageThought,
					selfIntro.preferredPartner,
					selfIntro.charmPoint,
					selfIntro.freeWord,
				),
			)
			.from(post)
			// 본문이 있어야 셀소 상세다. (본문 없는 글은 조회되지 않는다)
			.join(selfIntro).on(selfIntro.postId.eq(post.id))
			.leftJoin(userDetail).on(userDetail.userId.eq(post.userId))
			.leftJoin(region).on(region.id.eq(userDetail.regionId))
			.where(post.id.eq(postId), post.type.eq(LoungePostType.SELF_INTRO))
			.fetchFirst()
	}

	override fun findImageKeysByPostId(postId: Long): List<String> {
		val image: QLoungePostImageEntity = QLoungePostImageEntity.loungePostImageEntity
		return queryFactory
			.select(image.imageKey)
			.from(image)
			.where(image.postId.eq(postId))
			.orderBy(image.displayOrder.asc())
			.fetch()
	}

	companion object {
		/** 그리드 타일에 쓰는 대표 사진의 노출 순서. */
		private const val FIRST_PHOTO_ORDER: Int = 0
	}
}
