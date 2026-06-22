package com.org.meeple.infra.match.query

import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.scheduler.match.query.dao.GetRecommendableSoloUserDao
import com.org.meeple.scheduler.match.query.dto.RecommendableSoloUser
import com.querydsl.core.types.Projections
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * scheduler [GetRecommendableSoloUserDao]의 QueryDSL 구현. (조회 전용)
 * match_user 단독 베이스 + team_members NOT EXISTS로 팀 미소속 유저만 거른다.
 * (team_members @SQLRestriction이 소프트 삭제 행을 제외하므로, NOT EXISTS = 비삭제 소속이 전혀 없음 = 팀 미소속)
 * user_id 오름차순 키셋(ux_user_id) seek로 페이징한다. (filesort 없음)
 */
@Component
class GetRecommendableSoloUserDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetRecommendableSoloUserDao {

	override fun findTargets(cursor: Long?, limit: Int): List<RecommendableSoloUser> {
		val matchUser: QMatchUserEntity = QMatchUserEntity.matchUserEntity
		val teamMember: QTeamMemberEntity = QTeamMemberEntity.teamMemberEntity

		return queryFactory
			.select(
				Projections.constructor(
					RecommendableSoloUser::class.java,
					matchUser.userId,
					matchUser.gender,
					matchUser.regionCode,
				),
			)
			.from(matchUser)
			.where(
				cursor?.let { last: Long -> matchUser.userId.gt(last) },
				JPAExpressions.selectOne()
					.from(teamMember)
					.where(teamMember.userId.eq(matchUser.userId))
					.notExists(),
			)
			.orderBy(matchUser.userId.asc())
			.limit(limit.toLong())
			.fetch()
	}
}
