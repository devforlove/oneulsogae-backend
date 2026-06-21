package com.org.meeple.infra.match.query

import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.core.match.query.dao.GetReceivedInvitationsDao
import com.org.meeple.core.match.query.dto.ReceivedInvitation
import com.org.meeple.core.match.query.dto.ReceivedInvitationInviter
import com.org.meeple.infra.match.command.entity.QMatchUserEntity
import com.org.meeple.infra.match.command.entity.QTeamEntity
import com.org.meeple.infra.match.command.entity.QTeamMemberEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetReceivedInvitationsDao]의 QueryDSL 구현체. (조회 전용)
 * 요청자가 INVITED 구성원(me)인 INVITING 팀을 찾고, 같은 팀의 ACTIVE 구성원(owner=초대자)을 self-join해
 * 초대자 프로필(닉네임·프로필이미지·나이=match_user, 직업·회사명=user_details, 성별=team_members)과 함께 team id desc로 투영한다.
 * 팀당 ACTIVE 구성원은 1명이므로 항목당 1행. teams·team_members·user_details의 [org.hibernate.annotations.SQLRestriction]이 소프트 삭제 행을 제외한다. (match_user는 하드 삭제)
 */
@Component
class GetReceivedInvitationsDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetReceivedInvitationsDao {

	override fun findInvited(requesterId: Long): List<ReceivedInvitation> {
		val me: QTeamMemberEntity = QTeamMemberEntity("me")
		val owner: QTeamMemberEntity = QTeamMemberEntity("owner")
		val team: QTeamEntity = QTeamEntity.teamEntity
		val ownerMatch: QMatchUserEntity = QMatchUserEntity.matchUserEntity
		val ownerDetail: QUserDetailEntity = QUserDetailEntity.userDetailEntity

		return queryFactory
			.select(
				Projections.constructor(
					ReceivedInvitation::class.java,
					team.id,
					team.name,
					team.introduction,
					Projections.constructor(
						ReceivedInvitationInviter::class.java,
						owner.userId,
						ownerMatch.nickname,
						ownerDetail.job,
						ownerDetail.companyName,
						owner.gender,
						ownerMatch.profileImageCode,
						ownerMatch.age,
					),
				),
			)
			.from(me)
			.join(team).on(team.id.eq(me.teamId))
			.join(owner).on(owner.teamId.eq(team.id).and(owner.status.eq(TeamMemberStatus.ACTIVE)))
			.join(ownerMatch).on(ownerMatch.userId.eq(owner.userId))
			.join(ownerDetail).on(ownerDetail.userId.eq(owner.userId))
			.where(
				me.userId.eq(requesterId),
				me.status.eq(TeamMemberStatus.INVITED),
				team.status.eq(TeamStatus.INVITING),
			)
			.orderBy(team.id.desc())
			.fetch()
	}
}
