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
 * "구성원 중 [loginAfter] 이후 로그인한 사람"만 남긴 뒤(where) 팀별로 묶어(group by) 그 팀들을 후보로 본다.
 * 팀 성별·활동권역은 teams의 컬럼을 그대로 쓰고, lastLoginAt은 팀 구성원 최신 로그인(max)을 투영한다. (인메모리 TeamMatchPool 정렬용)
 * (teams·team_members의 @SQLRestriction이 소프트삭제 행을 제외한다)
 *
 * 인덱스: last_login_at 조건을 having이 아닌 where로 둬, 옵티마이저가 match_user.idx_last_login_at_user_id를 range로 구동하는
 * 계획(최근 로그인 유저 → team_members.idx_user_id → teams PK seek)도 선택할 수 있게 한다. (집계 후 필터였다면 구동 인덱스로 못 씀)
 * 의미는 having(max>=loginAfter)과 동일하다 — 팀 진짜 최신 로그인이 2주 내면 그 행이 살아남아 max도 그 값과 같고, 2주 밖이면 어느 쪽이든 제외된다.
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
			.where(
				team.status.eq(TeamStatus.ACTIVE),
				matchUser.lastLoginAt.goe(loginAfter),
			)
			.groupBy(team.id, team.gender, team.regionId)
			.fetch()
	}
}
