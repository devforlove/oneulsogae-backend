package com.org.meeple.infra.match.query

import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.scheduler.match.query.dao.GetCandidateTeamDao
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component
import java.util.concurrent.ThreadLocalRandom

/**
 * scheduler [GetCandidateTeamDao]의 QueryDSL 구현. (조회 전용)
 * teams 베이스에 두 EXISTS(① 팀 성별=teamGender, ② 팀원 중 한 명이라도 match_user.region_code=regionCode)로 후보를 좁힌다.
 * (EXISTS라 팀당 1행 → fan-out 없음) 후보 수를 센 뒤 [0,count) 랜덤 오프셋으로 1개의 team_id를 뽑는다.
 * (대규모에서 offset 스캔 비용이 커지면 커서/시드 기반으로 재검토한다 — 표시 전용 추천이라 현 단계에선 단순화)
 */
@Component
class GetCandidateTeamDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetCandidateTeamDao {

	override fun findOneCandidateTeamId(teamGender: Gender, regionCode: Int): Long? {
		val team: QTeamEntity = QTeamEntity.teamEntity

		val count: Long = queryFactory
			.select(team.count())
			.from(team)
			.where(*candidatePredicates(team, teamGender, regionCode))
			.fetchOne() ?: 0L
		if (count == 0L) return null

		val offset: Long = ThreadLocalRandom.current().nextLong(count)
		return queryFactory
			.select(team.id)
			.from(team)
			.where(*candidatePredicates(team, teamGender, regionCode))
			.orderBy(team.id.asc())
			.offset(offset)
			.limit(1)
			.fetchOne()
	}

	// 결성(ACTIVE) + 팀 성별=teamGender(팀은 동성 구성) + 팀원 중 한 명이라도 같은 권역.
	private fun candidatePredicates(team: QTeamEntity, teamGender: Gender, regionCode: Int): Array<BooleanExpression> {
		val genderTeamMember: QTeamMemberEntity = QTeamMemberEntity("genderTeamMember")
		val regionTeamMember: QTeamMemberEntity = QTeamMemberEntity("regionTeamMember")
		val matchUser: QMatchUserEntity = QMatchUserEntity.matchUserEntity
		return arrayOf(
			team.status.eq(TeamStatus.ACTIVE),
			JPAExpressions.selectOne()
				.from(genderTeamMember)
				.where(genderTeamMember.teamId.eq(team.id), genderTeamMember.gender.eq(teamGender))
				.exists(),
			JPAExpressions.selectOne()
				.from(regionTeamMember)
				.join(matchUser).on(matchUser.userId.eq(regionTeamMember.userId))
				.where(regionTeamMember.teamId.eq(team.id), matchUser.regionCode.eq(regionCode))
				.exists(),
		)
	}
}
