package com.org.meeple.infra.match.query

import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.core.match.query.dao.GetMyActiveTeamDao
import com.org.meeple.core.match.query.dto.MyActiveTeam
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetMyActiveTeamDao]의 QueryDSL 구현체. (조회 전용)
 * 요청자가 ACTIVE 구성원인 ACTIVE 팀에서 출발해(team_members idx_user_id seek + status 필터, teams PK 조인) 가장 최근 1팀을 잡고,
 * 같은 팀의 상대 ACTIVE 구성원(친구)을 함께 가져와 내/친구 profileImageCode를 [MatchUserEntity]에서 투영한다.
 * (2:2이므로 상대는 정확히 한 명) 결성 팀이 없으면 null.
 */
@Component
class GetMyActiveTeamDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetMyActiveTeamDao {

	override fun findLatestActiveTeam(userId: Long): MyActiveTeam? {
		val myTeamMember: QTeamMemberEntity = QTeamMemberEntity("myTeamMember")
		val team: QTeamEntity = QTeamEntity.teamEntity
		val partnerTeamMember: QTeamMemberEntity = QTeamMemberEntity("partnerTeamMember")
		val myMatchUser: QMatchUserEntity = QMatchUserEntity("myMatchUser")
		val partnerMatchUser: QMatchUserEntity = QMatchUserEntity("partnerMatchUser")

		return queryFactory
			.select(
				Projections.constructor(
					MyActiveTeam::class.java,
					team.id,
					team.gender,
					myMatchUser.profileImageCode,
					partnerMatchUser.profileImageCode,
				),
			)
			.from(myTeamMember)
			.join(team).on(team.id.eq(myTeamMember.teamId))
			.join(partnerTeamMember).on(
				partnerTeamMember.teamId.eq(team.id),
				partnerTeamMember.userId.ne(myTeamMember.userId),
				partnerTeamMember.status.eq(TeamMemberStatus.ACTIVE),
			)
			.join(myMatchUser).on(myMatchUser.userId.eq(myTeamMember.userId))
			.join(partnerMatchUser).on(partnerMatchUser.userId.eq(partnerTeamMember.userId))
			.where(
				myTeamMember.userId.eq(userId),
				myTeamMember.status.eq(TeamMemberStatus.ACTIVE),
				team.status.eq(TeamStatus.ACTIVE),
			)
			.orderBy(team.id.desc())
			.limit(1)
			.fetchOne()
	}
}
