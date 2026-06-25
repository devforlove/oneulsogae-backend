package com.org.meeple.infra.match.query

import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.core.match.query.dao.GetMyTeamDao
import com.org.meeple.core.match.query.dto.MyTeam
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetMyTeamDao]의 QueryDSL 구현체. (조회 전용)
 * 요청자가 ACTIVE 구성원인 ACTIVE 또는 INVITING(초대중) 팀에서 출발해(team_members idx_user_id seek + status 필터, teams PK 조인) 가장 최근 1팀을 잡고,
 * 같은 팀의 상대 구성원(친구 또는 초대 대상)을 함께 가져와 내/친구 profileImageCode를 [MatchUserEntity]에서 투영한다.
 * (2:2이므로 상대는 정확히 한 명. ACTIVE 팀이면 상대도 ACTIVE, INVITING 팀이면 상대는 INVITED) 속한 팀이 없으면 null.
 */
@Component
class GetMyTeamDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetMyTeamDao {

	override fun findMyTeam(userId: Long): MyTeam? {
		val myTeamMember: QTeamMemberEntity = QTeamMemberEntity("myTeamMember")
		val team: QTeamEntity = QTeamEntity.teamEntity
		val partnerTeamMember: QTeamMemberEntity = QTeamMemberEntity("partnerTeamMember")
		val myMatchUser: QMatchUserEntity = QMatchUserEntity("myMatchUser")
		val partnerMatchUser: QMatchUserEntity = QMatchUserEntity("partnerMatchUser")

		return queryFactory
			.select(
				Projections.constructor(
					MyTeam::class.java,
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
				// ACTIVE 팀이면 상대도 ACTIVE, INVITING 팀이면 상대는 초대중(INVITED)이다. 비활성(DEACTIVE)만 제외해 두 경우를 모두 잡는다.
				partnerTeamMember.status.ne(TeamMemberStatus.DEACTIVE),
			)
			.join(myMatchUser).on(myMatchUser.userId.eq(myTeamMember.userId))
			.join(partnerMatchUser).on(partnerMatchUser.userId.eq(partnerTeamMember.userId))
			.where(
				myTeamMember.userId.eq(userId),
				myTeamMember.status.eq(TeamMemberStatus.ACTIVE),
				// 결성(ACTIVE) 팀뿐 아니라 내가 만든 초대중(INVITING) 팀도 내 팀으로 본다. (요청자는 INVITING 팀의 ACTIVE 구성원=초대자)
				team.status.`in`(TeamStatus.ACTIVE, TeamStatus.INVITING),
			)
			.orderBy(team.id.desc())
			.limit(1)
			.fetchOne()
	}
}
