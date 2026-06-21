package com.org.meeple.infra.match.query

import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.core.match.query.dao.GetSentInvitationDao
import com.org.meeple.core.match.query.dto.SentInvitation
import com.org.meeple.core.match.query.dto.SentInvitationMember
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.querydsl.core.Tuple
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetSentInvitationDao]의 QueryDSL 구현체. (조회 전용)
 * ① 요청자가 ACTIVE 구성원(=초대자)인 INVITING 팀 헤더를 team.id desc로 1건 조회(team_members idx_user_id seek → status 필터 + teams PK 조인),
 * ② 그 팀의 구성원(userId·status)을 조회해 [SentInvitation]으로 조립한다.
 * [org.hibernate.annotations.SQLRestriction]이 소프트 삭제(철회·해체)된 행을 자동 제외한다. (신규 인덱스 불필요)
 */
@Component
class GetSentInvitationDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetSentInvitationDao {

	override fun findLatestInviting(requesterId: Long): SentInvitation? {
		val team: QTeamEntity = QTeamEntity.teamEntity
		val teamMember: QTeamMemberEntity = QTeamMemberEntity.teamMemberEntity

		val header: Tuple = queryFactory
			.select(team.id, team.name, team.introduction, team.status)
			.from(teamMember)
			.join(team).on(team.id.eq(teamMember.teamId))
			.where(
				teamMember.userId.eq(requesterId),
				teamMember.status.eq(TeamMemberStatus.ACTIVE),
				team.status.eq(TeamStatus.INVITING),
			)
			.orderBy(team.id.desc())
			.limit(1)
			.fetchOne()
			?: return null

		val teamId: Long = header.get(team.id)!!

		val members: List<SentInvitationMember> = queryFactory
			.select(Projections.constructor(SentInvitationMember::class.java, teamMember.userId, teamMember.status))
			.from(teamMember)
			.where(teamMember.teamId.eq(teamId))
			.orderBy(teamMember.userId.asc())
			.fetch()

		return SentInvitation(
			teamId = teamId,
			name = header.get(team.name)!!,
			introduction = header.get(team.introduction),
			status = header.get(team.status)!!,
			members = members,
		)
	}
}
