package com.org.meeple.infra.match.query

import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.scheduler.match.query.dao.GetMatchableTeamDao
import com.org.meeple.scheduler.match.query.dto.MatchableTeam
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * scheduler [GetMatchableTeamDao]의 QueryDSL 구현. (조회 전용)
 * teams(status=ACTIVE)에 그 팀의 ACTIVE 구성원(team_members)을 잇고, 구성원의 매칭 읽기 모델(match_user)을 user_id로 이어
 * 팀별로 묶은 뒤(group by) "구성원 최신 로그인(max(last_login_at)) >= loginAfter"인 팀만 남긴다(having).
 * 팀 성별·활동권역은 teams의 컬럼을 그대로 쓰고, lastLoginAt은 팀 구성원 최신 로그인을 투영한다. (인메모리 TeamMatchPool 구성용)
 * (teams·team_members의 @SQLRestriction이 소프트삭제 행을 제외한다)
 */
@Component
class GetMatchableTeamDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetMatchableTeamDao {

	override fun findMatchableTeams(loginAfter: LocalDateTime): List<MatchableTeam> {
		val team: QTeamEntity = QTeamEntity.teamEntity
		val teamMember: QTeamMemberEntity = QTeamMemberEntity.teamMemberEntity
		val matchUser: QMatchUserEntity = QMatchUserEntity.matchUserEntity
		return queryFactory
			.select(
				Projections.constructor(
					MatchableTeam::class.java,
					team.id,
					team.gender,
					team.regionId,
					matchUser.lastLoginAt.max(),
				),
			)
			.from(team)
			.join(teamMember).on(teamMember.teamId.eq(team.id).and(teamMember.status.eq(TeamMemberStatus.ACTIVE)))
			.join(matchUser).on(matchUser.userId.eq(teamMember.userId))
			.where(team.status.eq(TeamStatus.ACTIVE))
			.groupBy(team.id, team.gender, team.regionId)
			.having(matchUser.lastLoginAt.max().goe(loginAfter))
			.fetch()
	}
}
