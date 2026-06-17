package com.org.meeple.infra.match.query

import com.org.meeple.common.user.UserStatus
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.QUserEntity
import com.org.meeple.scheduler.match.query.dao.ActiveUserDao
import com.org.meeple.scheduler.match.query.dto.ActiveUser
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * scheduler [ActiveUserDao]의 QueryDSL 구현. 매칭 풀 그룹핑용 활성(ACTIVE) + 최근 로그인 사용자를 조회한다.
 * 그룹 키 산출에 필요한 (userId, gender, regionCode)만 user_details와 명시 조인해 [ActiveUser] read model로 직접 투영한다.
 * 성별·권역 null 필터는 두지 않는다. ACTIVE 사용자는 프로필 검증으로 둘 다 채워짐이 보장되어 non-null 투영이 안전하다.
 * (@SQLRestriction으로 두 엔티티의 soft delete된 행은 자동 제외된다)
 */
@Component
class ActiveUserDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : ActiveUserDao {

	override fun findActiveUsers(loginAfter: LocalDateTime): List<ActiveUser> {
		val user: QUserEntity = QUserEntity.userEntity
		val detail: QUserDetailEntity = QUserDetailEntity.userDetailEntity

		return queryFactory
			.select(
				Projections.constructor(
					ActiveUser::class.java,
					user.id,
					detail.gender,
					detail.regionCode,
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
