package com.org.oneulsogae.infra.teammatch.query

import com.org.oneulsogae.common.match.TeamMemberStatus
import com.org.oneulsogae.common.match.TeamStatus
import com.org.oneulsogae.core.teammatch.query.dao.GetSentInvitationDao
import com.org.oneulsogae.core.teammatch.query.dto.SentInvitation
import com.org.oneulsogae.core.teammatch.query.dto.SentInvitationMember
import com.org.oneulsogae.infra.matchuser.command.entity.QMatchUserEntity
import com.org.oneulsogae.infra.teammatch.command.entity.QTeamEntity
import com.org.oneulsogae.infra.teammatch.command.entity.QTeamMemberEntity
import com.org.oneulsogae.infra.region.entity.QRegionEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.querydsl.core.types.Projections
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.core.types.dsl.StringExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Component

/**
 * [GetSentInvitationDao]의 QueryDSL 구현체. (조회 전용)
 * ① 요청자가 ACTIVE 구성원(=초대자)인 INVITING·ACTIVE 팀 헤더를 team.id desc로 1건 조회(team_members idx_user_id seek → status 필터 + teams PK 조인),
 * ② 그 팀의 구성원 전원을 표시용 프로필과 함께 조회해 [SentInvitation]으로 조립한다.
 *    (요청자 본인(ACTIVE)도 포함한다. INVITING이면 초대자(ACTIVE)+초대 대상(INVITED), ACTIVE면 전원 ACTIVE)
 *    (닉네임·프로필이미지는 match_user, 직업·회사명은 user_details 조인. 성별은 team_members에 보관된 값을 쓴다)
 * [org.hibernate.annotations.SQLRestriction]이 소프트 삭제(철회·해체)된 행을 자동 제외한다. (신규 인덱스 불필요)
 */
@Component
class GetSentInvitationDaoImpl(
	private val queryFactory: JPAQueryFactory,
) : GetSentInvitationDao {

	override fun findLatestSentInvitation(userId: Long): SentInvitation? {
		val invitation: SentInvitation = findLatestInvitingTeam(userId) ?: return null
		return invitation.copy(members = findTeamMembers(invitation.teamId))
	}

	// ① 요청자가 ACTIVE 구성원(=초대자)인 INVITING·ACTIVE 팀 헤더 1건을 team.id desc로 조회한다. (구성원은 ②에서 채우므로 빈 목록으로 둔다)
	// 표시용 활동지역은 regions를 left join해 "시/도 시/군/구"로 만든다. (지역 미설정/미존재면 null)
	private fun findLatestInvitingTeam(userId: Long): SentInvitation? {
		val team: QTeamEntity = QTeamEntity.teamEntity
		val teamMember: QTeamMemberEntity = QTeamMemberEntity.teamMemberEntity
		val region: QRegionEntity = QRegionEntity.regionEntity
		val activityArea: StringExpression = region.sido.concat(" ").concat(region.sigungu)

		return queryFactory
			.select(
				Projections.constructor(
					SentInvitation::class.java,
					team.id,
					team.name,
					team.regionId,
					activityArea,
					team.introduction,
					team.status,
					Expressions.constant(emptyList<SentInvitationMember>()),
				),
			)
			.from(teamMember)
			.join(team).on(team.id.eq(teamMember.teamId))
			.leftJoin(region).on(region.id.eq(team.regionId))
			.where(
				teamMember.userId.eq(userId),
				teamMember.status.eq(TeamMemberStatus.ACTIVE),
				// 초대중(INVITING)·결성(ACTIVE)뿐 아니라 해체중(DISBANDED, 남은 구성원) 팀도 내 팀으로 본다. (구성원은 ②에서 @SQLRestriction이 나간 구성원을 제외해 남은 인원만 채운다)
				team.status.`in`(TeamStatus.INVITING, TeamStatus.ACTIVE, TeamStatus.DISBANDED),
			)
			.orderBy(team.id.desc())
			.limit(1)
			.fetchOne()
	}

	// ② 그 팀의 구성원 전원을 표시용 프로필과 함께 조회한다. (요청자 본인(ACTIVE) 포함; 닉네임·프로필이미지=match_user, 직업·회사명=user_details)
	private fun findTeamMembers(teamId: Long): List<SentInvitationMember> {
		val teamMember: QTeamMemberEntity = QTeamMemberEntity.teamMemberEntity
		val matchUser: QMatchUserEntity = QMatchUserEntity.matchUserEntity
		val userDetail: QUserDetailEntity = QUserDetailEntity.userDetailEntity

		return queryFactory
			.select(
				Projections.constructor(
					SentInvitationMember::class.java,
					teamMember.userId,
					matchUser.nickname,
					userDetail.job,
					userDetail.companyName,
					userDetail.universityName,
					matchUser.gender,
					matchUser.profileImageCode,
					matchUser.birthday,
					teamMember.status,
				),
			)
			.from(teamMember)
			.join(matchUser).on(matchUser.userId.eq(teamMember.userId))
			.join(userDetail).on(userDetail.userId.eq(teamMember.userId))
			.where(teamMember.teamId.eq(teamId))
			.orderBy(teamMember.userId.asc())
			.fetch()
	}
}
