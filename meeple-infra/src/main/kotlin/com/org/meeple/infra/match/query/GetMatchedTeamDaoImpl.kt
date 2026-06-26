package com.org.meeple.infra.match.query

import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.MatchedTeamStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.core.match.query.dao.GetMatchedTeamDao
import com.org.meeple.core.match.query.dto.RecommendedTeam
import com.org.meeple.core.match.query.dto.RecommendedTeamMember
import com.org.meeple.infra.match.command.entity.QMatchedTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMatchEntity
import com.org.meeple.infra.region.entity.QRegionEntity
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.core.types.dsl.StringExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * [GetMatchedTeamDao]의 QueryDSL 구현체. (조회 전용)
 * ① 내 참가 행(matched_teams mine) 기준으로 같은 팀 매칭(team_matches)의 상대 참가 행(opp)을 이어 상대 팀(teams ACTIVE)(⟕ regions) 헤더를 최신순으로 조회하고,
 * ② 그 상대 팀들의 ACTIVE 구성원을 [RecommendedTeamMemberLoader]로 한 번에 조회해 팀별로 묶어 [RecommendedTeam] 리스트로 조립한다.
 * 진행 중 필터: 팀 매칭이 종료(CLOSED) 아님 + [now] 미만료, 내 참가 status가 DEACTIVE 아님(내가 나간 매칭 제외), 상대 팀 status가 ACTIVE. 상대 참가 status는 거르지 않아 상대가 나가도(DEACTIVE) 카드는 남는다. (matched_teams·team_matches·teams의 @SQLRestriction이 소프트삭제 제외)
 *
 * 인덱스: mine은 matched_teams.team_id seek(idx_team_id), opp는 (team_match_id, team_id) 유니크(ux_team_match_id_team_id) 선두 team_match_id seek + team_matches·teams PK 조인이라 풀스캔이 없다. (구성원 쪽 인덱스는 로더 참고)
 */
@Component
class GetMatchedTeamDaoImpl(
	private val queryFactory: JPAQueryFactory,
	private val recommendedTeamMemberLoader: RecommendedTeamMemberLoader,
) : GetMatchedTeamDao {

	override fun findInProgressByTeamId(myTeamId: Long, now: LocalDateTime): List<RecommendedTeam> {
		val teams: List<RecommendedTeam> = findInProgressOpponentTeams(myTeamId, now)
		if (teams.isEmpty()) return emptyList()

		val membersByTeamId: Map<Long, List<RecommendedTeamMember>> =
			recommendedTeamMemberLoader.loadByTeamIds(teams.map { team: RecommendedTeam -> team.teamId })

		return teams.map { team: RecommendedTeam -> team.copy(members = membersByTeamId[team.teamId].orEmpty()) }
	}

	// ① 내 참가 행(mine) 기준으로 같은 팀 매칭의 상대 참가 행(opp)을 이어 진행 중인 상대 팀(ACTIVE) 헤더를 최신순으로 조회한다. (구성원은 ②에서 채우므로 빈 목록으로 둔다)
	// 관심(신청) 여부는 참가 status가 APPLY(신청)/ACTIVE(성사)인지로 본다. 팀 활동지역은 regions를 left join해 "시/도 시/군/구"로 만든다. (지역 미설정이면 null)
	private fun findInProgressOpponentTeams(myTeamId: Long, now: LocalDateTime): List<RecommendedTeam> {
		val mine: QMatchedTeamEntity = QMatchedTeamEntity("mine")
		val opp: QMatchedTeamEntity = QMatchedTeamEntity("opp")
		val teamMatch: QTeamMatchEntity = QTeamMatchEntity.teamMatchEntity
		val team: QTeamEntity = QTeamEntity.teamEntity
		val teamRegion: QRegionEntity = QRegionEntity("teamRegion")
		val teamActivityArea: StringExpression = teamRegion.sido.concat(" ").concat(teamRegion.sigungu)

		return queryFactory
			.select(
				Projections.constructor(
					RecommendedTeam::class.java,
					team.id,
					team.name,
					team.introduction,
					teamActivityArea,
					Expressions.constant(emptyList<RecommendedTeamMember>()),
					teamMatch.dateInitAmount,
					teamMatch.dateAcceptAmount,
					teamMatch.id,
					teamMatch.status,
					mine.status.`in`(MatchedTeamStatus.APPLY, MatchedTeamStatus.ACTIVE),
					opp.status.`in`(MatchedTeamStatus.APPLY, MatchedTeamStatus.ACTIVE),
				),
			)
			.from(mine)
			.join(teamMatch).on(teamMatch.id.eq(mine.teamMatchId))
			.join(opp).on(
				opp.teamMatchId.eq(mine.teamMatchId),
				opp.teamId.ne(mine.teamId),
				// 상대 팀 status는 거르지 않는다. 상대 팀이 매칭을 나갔어도(DEACTIVE) 내 목록엔 남겨 상대 팀과 함께 보여준다.
			)
			.join(team).on(team.id.eq(opp.teamId))
			.leftJoin(teamRegion).on(teamRegion.id.eq(team.regionId))
			.where(
				mine.teamId.eq(myTeamId),
				// 내 팀이 나간(DEACTIVE) 매칭만 내 목록에서 제외한다. (대기·신청·활성 매칭은 노출)
				mine.status.ne(MatchedTeamStatus.DEACTIVE),
				team.status.eq(TeamStatus.ACTIVE),
				teamMatch.status.ne(MatchStatus.CLOSED),
				teamMatch.expiresAt.gt(now),
			)
			.orderBy(teamMatch.introducedDate.desc(), team.id.desc())
			.fetch()
	}
}
