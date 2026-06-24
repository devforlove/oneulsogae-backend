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
import com.querydsl.core.Tuple
import com.querydsl.core.types.dsl.StringExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * [GetMatchedTeamDao]의 QueryDSL 구현체. (조회 전용)
 * ① 내 참가 행(matched_teams mine) 기준으로 같은 팀 매칭(team_matches)의 상대 참가 행(opp)을 이어 상대 팀(teams ACTIVE)(⟕ regions) 헤더를 최신순으로 조회하고,
 * ② 그 상대 팀들의 ACTIVE 구성원을 [RecommendedTeamMemberLoader]로 한 번에 조회해 팀별로 묶어 [RecommendedTeam] 리스트로 조립한다.
 * 진행 중 필터: 팀 매칭이 종료(CLOSED) 아님 + [now] 미만료, 상대 참가 status가 DEACTIVE 아님, 상대 팀 status가 ACTIVE. (matched_teams·team_matches·teams의 @SQLRestriction이 소프트삭제 제외)
 *
 * 인덱스: mine은 matched_teams.team_id seek(idx_team_id), opp는 (team_match_id, team_id) 유니크(ux_team_match_id_team_id) 선두 team_match_id seek + team_matches·teams PK 조인이라 풀스캔이 없다. (구성원 쪽 인덱스는 로더 참고)
 */
@Component
class GetMatchedTeamDaoImpl(
	private val queryFactory: JPAQueryFactory,
	private val recommendedTeamMemberLoader: RecommendedTeamMemberLoader,
) : GetMatchedTeamDao {

	override fun findInProgressByTeamId(myTeamId: Long, now: LocalDateTime): List<RecommendedTeam> {
		val mine: QMatchedTeamEntity = QMatchedTeamEntity("mine")
		val opp: QMatchedTeamEntity = QMatchedTeamEntity("opp")
		val teamMatch: QTeamMatchEntity = QTeamMatchEntity.teamMatchEntity
		val team: QTeamEntity = QTeamEntity.teamEntity
		val teamRegion: QRegionEntity = QRegionEntity("teamRegion")

		// 팀 활동지역은 teams.region_id를 regions에 join해 "시/도 시/군/구"로 만든다. (지역 미설정이면 null)
		val teamActivityArea: StringExpression = teamRegion.sido.concat(" ").concat(teamRegion.sigungu)

		val headers: List<Tuple> = queryFactory
			.select(team.id, team.name, team.introduction, teamActivityArea)
			.from(mine)
			.join(teamMatch).on(teamMatch.id.eq(mine.teamMatchId))
			.join(opp).on(opp.teamMatchId.eq(mine.teamMatchId).and(opp.teamId.ne(mine.teamId)))
			.join(team).on(team.id.eq(opp.teamId))
			.leftJoin(teamRegion).on(teamRegion.id.eq(team.regionId))
			.where(
				mine.teamId.eq(myTeamId),
				opp.status.ne(MatchedTeamStatus.DEACTIVE),
				team.status.eq(TeamStatus.ACTIVE),
				teamMatch.status.ne(MatchStatus.CLOSED),
				teamMatch.expiresAt.gt(now),
			)
			.orderBy(teamMatch.introducedDate.desc(), team.id.desc())
			.fetch()

		if (headers.isEmpty()) return emptyList()

		val teamIds: List<Long> = headers.map { header: Tuple -> header.get(team.id)!! }
		val membersByTeamId: Map<Long, List<RecommendedTeamMember>> = recommendedTeamMemberLoader.loadByTeamIds(teamIds)

		return headers.map { header: Tuple ->
			val teamId: Long = header.get(team.id)!!
			RecommendedTeam(
				teamId = teamId,
				name = header.get(team.name)!!,
				introduction = header.get(team.introduction),
				activityArea = header.get(teamActivityArea),
				members = membersByTeamId[teamId].orEmpty(),
			)
		}
	}
}
