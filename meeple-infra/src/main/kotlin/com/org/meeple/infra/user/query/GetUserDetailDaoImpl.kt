package com.org.meeple.infra.user.query

import com.org.meeple.core.user.query.dao.GetUserDetailDao
import com.org.meeple.core.user.query.dto.UserDetailView
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetUserDetailDao]의 QueryDSL 구현체. (조회 전용)
 * 엔티티를 거치지 않고 [UserDetailView] read model로 바로 투영한다.
 * traits/interests는 `@Convert`(JSON) 컬럼이라 QueryDSL 메타모델이 `ListPath`(컬렉션)로 만들어 그대로 select하면 컨버터가 적용되지 않으므로, [Expressions.path]로 기본 속성 경로로 참조한다.
 * 명령 흐름의 단건 로드는 command 쪽 [com.org.meeple.infra.user.command.adapter.UserDetailCoreAdapter]가 메서드 쿼리로 따로 구현한다.
 */
@Component
class GetUserDetailDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetUserDetailDao {

	override fun findByUserId(userId: Long): UserDetailView? {
		val detail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		return queryFactory
			.select(
				Projections.constructor(
					UserDetailView::class.java,
					detail.id,
					detail.userId,
					detail.nickname,
					detail.profileImageCode,
					detail.age,
					detail.height,
					detail.gender,
					detail.phoneNumber,
					detail.job,
					detail.activityArea,
					detail.regionCode,
					detail.introduction,
					Expressions.path(List::class.java, detail, "traits"),
					Expressions.path(List::class.java, detail, "interests"),
					detail.companyEmail,
					detail.companyName,
					detail.maritalStatus,
					detail.smokingStatus,
					detail.religion,
					detail.drinkingStatus,
					detail.bodyType,
				)
			)
			.from(detail)
			.where(detail.userId.eq(userId))
			.fetchOne()
	}
}
