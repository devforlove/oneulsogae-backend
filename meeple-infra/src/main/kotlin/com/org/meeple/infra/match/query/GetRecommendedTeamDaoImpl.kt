package com.org.meeple.infra.match.query

import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.core.match.query.dao.GetRecommendedTeamDao
import com.org.meeple.core.match.query.dto.RecommendedTeam
import com.org.meeple.core.match.query.dto.RecommendedTeamMember
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QRecommendedTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.querydsl.core.Tuple
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetRecommendedTeamDao]의 QueryDSL 구현체. (조회 전용)
 * ① 추천 행(recommended_teams) ⋈ teams(ACTIVE) 헤더를 단건 조회(유저당 추천 1개, teams @SQLRestriction이 해체 팀 제외),
 * ② 그 팀의 ACTIVE 구성원을 team_members ⋈ match_user로 프로필과 함께 조회해 [RecommendedTeam]으로 조립한다.
 * 추천이 없거나 팀이 해체(soft delete/비ACTIVE)됐으면 null.
 */
@Component
class GetRecommendedTeamDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetRecommendedTeamDao {

	override fun findByUserId(userId: Long): RecommendedTeam? {
		val recommended: QRecommendedTeamEntity = QRecommendedTeamEntity.recommendedTeamEntity
		val team: QTeamEntity = QTeamEntity.teamEntity

		val header: Tuple = queryFactory
			.select(team.id, team.name, team.introduction)
			.from(recommended)
			.join(team).on(team.id.eq(recommended.teamId))
			.where(
				recommended.userId.eq(userId),
				team.status.eq(TeamStatus.ACTIVE),
			)
			.fetchOne()
			?: return null

		val teamId: Long = header.get(team.id)!!

		val member: QTeamMemberEntity = QTeamMemberEntity.teamMemberEntity
		val matchUser: QMatchUserEntity = QMatchUserEntity.matchUserEntity
		val members: List<RecommendedTeamMember> = queryFactory
			.select(
				Projections.constructor(
					RecommendedTeamMember::class.java,
					member.userId,
					matchUser.nickname,
					matchUser.gender,
					matchUser.profileImageCode,
					matchUser.birthday,
				),
			)
			.from(member)
			.join(matchUser).on(matchUser.userId.eq(member.userId))
			.where(
				member.teamId.eq(teamId),
				member.status.eq(TeamMemberStatus.ACTIVE),
			)
			.orderBy(member.userId.asc())
			.fetch()

		return RecommendedTeam(
			teamId = teamId,
			name = header.get(team.name)!!,
			introduction = header.get(team.introduction),
			members = members,
		)
	}
}
