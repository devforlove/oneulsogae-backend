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
import com.querydsl.core.Tuple
import com.querydsl.core.types.ConstructorExpression
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetReceivedInvitationsDao]의 QueryDSL 구현체. (조회 전용)
 * 한 팀에 ACTIVE 구성원이 여러 명일 수 있어, 단일 조인으로 뽑으면 초대 1건이 멤버 수만큼 fan-out(중복)된다.
 * 이를 막기 위해 [GetSentInvitationDaoImpl]처럼 두 쿼리로 분리한다.
 * ① 내가 INVITED 구성원인 INVITING 팀 헤더(팀 메타 + 내가 초대된 시각 me.created_at)를 team.id desc로 조회(1팀당 1행),
 * ② 그 팀들의 ACTIVE 구성원 프로필을 teamId in (…)로 한 번에 조회해 teamId로 그룹핑한 뒤 헤더에 끼운다.
 *    (닉네임·프로필이미지·나이=match_user, 직업·회사명·키·지역·자기소개·특성·관심사=user_details, 성별=team_members)
 * [org.hibernate.annotations.SQLRestriction]이 소프트 삭제 행을 제외한다. (match_user는 하드 삭제)
 * - traits/interests는 `@Convert`(JSON) 컬럼이라 QueryDSL 메타모델이 `ListPath`(컬렉션)로 만들어 그대로 select하면 컨버터가 적용되지 않으므로, [Expressions.path]로 기본 속성 경로로 참조한다.
 */
@Component
class GetReceivedInvitationsDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetReceivedInvitationsDao {

	override fun findInvited(userId: Long): List<ReceivedInvitation> {
		val me: QTeamMemberEntity = QTeamMemberEntity("me")
		val team: QTeamEntity = QTeamEntity.teamEntity

		// ① 내가 INVITED인 INVITING 팀 헤더. (팀당 1행, fan-out 없음)
		val headers: List<Tuple> = queryFactory
			.select(team.id, team.name, team.introduction, me.createdAt)
			.from(me)
			.join(team).on(team.id.eq(me.teamId))
			.where(
				me.userId.eq(userId),
				me.status.eq(TeamMemberStatus.INVITED),
				team.status.eq(TeamStatus.INVITING),
			)
			.orderBy(team.id.desc())
			.fetch()

		if (headers.isEmpty()) return emptyList()

		val teamIds: List<Long> = headers.map { header: Tuple -> header.get(team.id)!! }
		val invitersByTeamId: Map<Long, List<ReceivedInvitationInviter>> = findActiveInvitersByTeamIds(teamIds)

		return headers.map { header: Tuple ->
			val teamId: Long = header.get(team.id)!!
			ReceivedInvitation(
				teamId = teamId,
				name = header.get(team.name)!!,
				introduction = header.get(team.introduction),
				invitedAt = header.get(me.createdAt)!!,
				inviters = invitersByTeamId[teamId] ?: emptyList(),
			)
		}
	}

	// ② 주어진 팀들의 ACTIVE 구성원 프로필을 한 번에 조회해 teamId → 구성원(생성 순) 맵으로 반환한다.
	private fun findActiveInvitersByTeamIds(teamIds: List<Long>): Map<Long, List<ReceivedInvitationInviter>> {
		val inviter: QTeamMemberEntity = QTeamMemberEntity("inviter")
		val inviterMatch: QMatchUserEntity = QMatchUserEntity.matchUserEntity
		val inviterDetail: QUserDetailEntity = QUserDetailEntity.userDetailEntity

		val inviterProjection: ConstructorExpression<ReceivedInvitationInviter> = Projections.constructor(
			ReceivedInvitationInviter::class.java,
			inviter.userId,
			inviterMatch.nickname,
			inviterDetail.job,
			inviterDetail.companyName,
			inviter.gender,
			inviterMatch.profileImageCode,
			inviterMatch.birthday,
			inviterDetail.height,
			inviterDetail.activityArea,
			inviterDetail.introduction,
			Expressions.path(List::class.java, inviterDetail, "traits"),
			Expressions.path(List::class.java, inviterDetail, "interests"),
		)

		return queryFactory
			.select(inviter.teamId, inviterProjection)
			.from(inviter)
			.join(inviterMatch).on(inviterMatch.userId.eq(inviter.userId))
			.join(inviterDetail).on(inviterDetail.userId.eq(inviter.userId))
			.where(
				inviter.teamId.`in`(teamIds),
				inviter.status.eq(TeamMemberStatus.ACTIVE),
			)
			// 생성 순(가장 먼저 만든 ACTIVE=생성자가 앞) 정렬 → 화면에서 members[0]을 대표 초대자로 쓸 수 있다.
			.orderBy(inviter.id.asc())
			.fetch()
			.groupBy({ row: Tuple -> row.get(inviter.teamId)!! }, { row: Tuple -> row.get(inviterProjection)!! })
	}
}
