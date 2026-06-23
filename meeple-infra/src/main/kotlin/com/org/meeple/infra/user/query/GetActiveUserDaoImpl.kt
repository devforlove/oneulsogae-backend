package com.org.meeple.infra.user.query

import com.org.meeple.common.user.UserStatus
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.QUserEntity
import com.org.meeple.scheduler.match.query.dao.GetActiveUserDao
import com.org.meeple.scheduler.match.query.dto.ActiveUser
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * scheduler [GetActiveUserDao]의 QueryDSL 구현. 활성 사용자 조회는 user 엔티티를 다루므로 user 도메인 infra(query)에 둔다. (조회 전용)
 * 그룹 키 산출에 필요한 (userId, gender, regionId)만 user_details와 명시 조인해 scheduler read model([ActiveUser])로 직접 투영한다.
 * 성별·권역 null 필터는 두지 않는다. ACTIVE 사용자는 프로필 검증으로 둘 다 채워짐이 보장되어 non-null 투영이 안전하다.
 * (@SQLRestriction으로 두 엔티티의 soft delete된 행은 자동 제외된다)
 */
@Component
class GetActiveUserDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetActiveUserDao {

	override fun findActiveUsers(loginAfter: LocalDateTime): List<ActiveUser> {
		val user: QUserEntity = QUserEntity.userEntity
		val detail: QUserDetailEntity = QUserDetailEntity.userDetailEntity

		return queryFactory
			.select(
				Projections.constructor(
					ActiveUser::class.java,
					user.id,
					detail.gender,
					detail.regionId,
				),
			)
			.from(user)
			.join(detail).on(detail.userId.eq(user.id))
			.where(
				user.status.eq(UserStatus.ACTIVE),
				user.lastLoginAt.goe(loginAfter),
			)
			.orderBy(user.lastLoginAt.asc(), user.id.asc())
			.fetch()
	}
}
