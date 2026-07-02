package com.org.meeple.infra.solomatch.query

import com.org.meeple.infra.matchuser.command.entity.QMatchUserEntity
import com.org.meeple.scheduler.solomatch.query.dao.GetMatchableUserDao
import com.org.meeple.scheduler.solomatch.query.dto.MatchableUser
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * scheduler [GetMatchableUserDao]의 QueryDSL 구현. match_user 단독으로 활성 유저를 [MatchableUser]로 투영한다.
 * `last_login_at >= :loginAfter` 범위는 `idx_last_login_at_user_id`로 받쳐져 풀 테이블 스캔이 아니다. (활성 집합만 읽는다)
 */
@Component
class GetMatchableUserDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetMatchableUserDao {

	override fun findMatchableUsers(loginAfter: LocalDateTime): List<MatchableUser> {
		val matchUser: QMatchUserEntity = QMatchUserEntity.matchUserEntity
		return queryFactory
			.select(
				Projections.constructor(
					MatchableUser::class.java,
					matchUser.userId,
					matchUser.gender,
					matchUser.regionId,
					matchUser.lastLoginAt,
					matchUser.companyName,
					matchUser.refuseSameCompanyIntro,
				),
			)
			.from(matchUser)
			.where(matchUser.lastLoginAt.goe(loginAfter))
			.orderBy(matchUser.lastLoginAt.desc())
			.fetch()
	}
}
