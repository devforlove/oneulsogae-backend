package com.org.oneulsogae.infra.user.query

import com.org.oneulsogae.core.user.query.dao.GetUserDetailDao
import com.org.oneulsogae.core.user.query.dto.UserDetailView
import com.org.oneulsogae.infra.matchuser.command.entity.QMatchUserEntity
import com.org.oneulsogae.infra.region.entity.QRegionEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetUserDetailDao]의 QueryDSL 구현체. (조회 전용)
 * 엔티티를 거치지 않고 [UserDetailView] read model로 바로 투영한다.
 * traits/interests는 `@Convert`(JSON) 컬럼이라 QueryDSL 메타모델이 `ListPath`(컬렉션)로 만들어 그대로 select하면 컨버터가 적용되지 않으므로, [Expressions.path]로 기본 속성 경로로 참조한다.
 * 명령 흐름의 단건 로드는 command 쪽 [com.org.oneulsogae.infra.user.command.adapter.UserDetailCoreAdapter]가 메서드 쿼리로 따로 구현한다.
 */
@Component
class GetUserDetailDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetUserDetailDao {

	override fun findByUserId(userId: Long): UserDetailView? {
		val detail: QUserDetailEntity = QUserDetailEntity.userDetailEntity
		val region: QRegionEntity = QRegionEntity.regionEntity
		val matchUser: QMatchUserEntity = QMatchUserEntity.matchUserEntity
		return queryFactory
			.select(
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
					detail.introduction,
					Expressions.path(List::class.java, detail, "traits"),
					Expressions.path(List::class.java, detail, "interests"),
					detail.companyEmail,
					detail.companyName,
					detail.universityEmail,
					detail.universityName,
					detail.secondaryEmail,
					detail.maritalStatus,
					detail.smokingStatus,
					detail.religion,
					detail.drinkingStatus,
					detail.bodyType,
					// 같은 회사 소개 거부 플래그는 match_user를 join해 채운다. (행이 없으면 기본값 거부 true)
					matchUser.refuseSameCompanyIntro.coalesce(true),
				)
			)
			.from(detail)
			.leftJoin(region).on(region.id.eq(detail.regionId))
			.leftJoin(matchUser).on(matchUser.userId.eq(detail.userId))
			.where(detail.userId.eq(userId))
			.fetchOne()
	}
}
