package com.org.oneulsogae.infra.user.query

import com.org.oneulsogae.core.user.query.dao.GetReferralSummaryDao
import com.org.oneulsogae.core.user.query.dto.ReferralSummary
import com.org.oneulsogae.infra.user.command.entity.QReferralRewardGrantEntity
import com.querydsl.core.Tuple
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.core.types.dsl.NumberExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetReferralSummaryDao]의 QueryDSL 구현. (조회 전용)
 * 추천 보상 지급 이력에서 건수와 코인 합계를 한 번에 집계한다. (실제 지급된 건만 세므로 인원수와 코인이 어긋나지 않는다)
 * referrer_user_id 동등 조건이 `idx_referrer_user_id`를 그대로 타 seek로 끝난다.
 */
@Component
class GetReferralSummaryDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetReferralSummaryDao {

	override fun findSummary(referrerUserId: Long): ReferralSummary {
		val grant: QReferralRewardGrantEntity = QReferralRewardGrantEntity.referralRewardGrantEntity
		// NumberPath.sum()은 Kotlin에서 private 오버로드(sum(Class))로 잡혀 컴파일되지 않아 템플릿으로 집계한다.
		// MySQL의 sum()은 BIGINT를 돌려주므로 Long으로 받아 Int로 좁힌다. (코인 합계가 Int 범위를 넘을 일은 없다)
		val coinSum: NumberExpression<Long> = Expressions.numberTemplate(Long::class.java, "sum({0})", grant.coinAmount)
		val row: Tuple? = queryFactory
			.select(grant.count(), coinSum)
			.from(grant)
			.where(grant.referrerUserId.eq(referrerUserId))
			.fetchFirst()
		return ReferralSummary(
			referredUserCount = row?.get(grant.count())?.toInt() ?: 0,
			// 집계 대상 행이 없으면 sum()은 null이다.
			earnedCoinAmount = row?.get(coinSum)?.toInt() ?: 0,
		)
	}
}
