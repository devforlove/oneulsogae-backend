package com.org.oneulsogae.infra.user.query

import com.org.oneulsogae.core.user.query.dao.GetReferralSummaryDao
import com.org.oneulsogae.infra.user.command.entity.QUserEntity
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetReferralSummaryDao]의 QueryDSL 구현. (조회 전용)
 * referred_by_user_id 동등 조건이 `idx_referred_by_user_id`를 그대로 타 seek로 끝난다.
 * (users의 @SQLRestriction이 탈퇴로 소프트 삭제된 피추천인을 제외한다)
 */
@Component
class GetReferralSummaryDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetReferralSummaryDao {

	override fun countReferredUsers(referrerUserId: Long): Int {
		val user: QUserEntity = QUserEntity.userEntity
		return queryFactory
			.select(user.count())
			.from(user)
			.where(user.referredByUserId.eq(referrerUserId))
			.fetchFirst()
			?.toInt() ?: 0
	}
}
