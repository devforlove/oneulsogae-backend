package com.org.meeple.infra.user.query

import com.org.meeple.core.user.query.dao.GetUserWithDetailDao
import com.org.meeple.core.user.query.dto.UserDetailView
import com.org.meeple.core.user.query.dto.UserView
import com.org.meeple.core.user.query.dto.UserWithDetailView
import com.org.meeple.infra.region.entity.QRegionEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.QUserEntity
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetUserWithDetailDao]의 QueryDSL 구현체. (조회 전용)
 * 사용자와 프로필 상세를 명시적 조인으로 한 번에 가져와(1+N 방지) 평탄 read model([UserWithDetailView])로 바로 투영한다.
 * traits/interests는 `@Convert`(JSON) 컬럼이라 QueryDSL 메타모델이 `ListPath`(컬렉션)로 만들어 그대로 select하면 컨버터가 적용되지 않으므로, [Expressions.path]로 기본 속성 경로로 참조한다.
 */
@Component
class GetUserWithDetailDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetUserWithDetailDao {

	override fun findWithDetailByUserId(userId: Long): UserWithDetailView? {
		val user: QUserEntity = QUserEntity.userEntity
		val detail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		val region: QRegionEntity = QRegionEntity.regionEntity

		return queryFactory
			.select(
				Projections.constructor(
					UserWithDetailView::class.java,
					Projections.constructor(
						UserView::class.java,
						user.id,
						user.email,
						user.status,
					),
					Projections.constructor(
						UserDetailView::class.java,
						detail.id,
						detail.userId,
						detail.nickname,
						detail.profileImageCode,
						detail.birthday,
						detail.height,
						detail.gender,
						detail.phoneNumber,
						detail.job,
						detail.regionId,
						// 표시용 활동지역은 regions를 join해 "시/도 시/군/구"로 만든다. (지역 미설정이면 null)
						region.sido.concat(" ").concat(region.sigungu),
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
					),
				),
			)
			.from(user)
			.join(detail).on(detail.userId.eq(user.id))
			.leftJoin(region).on(region.id.eq(detail.regionId))
			.where(user.id.eq(userId))
			.fetchOne()
	}
}
