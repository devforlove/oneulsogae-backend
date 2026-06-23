package com.org.meeple.api.match.response

import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.core.common.time.ageAt
import com.org.meeple.core.match.query.dto.SentInvitation
import com.org.meeple.core.match.query.dto.SentInvitationMember
import java.time.LocalDate

/**
 * 내가 보낸 초대 현황 응답. 팀 식별자·이름·소개·상태와 구성원 프로필을 담는다.
 * (INVITING이면 초대 대상(INVITED)만, ACTIVE면 전원(요청자 본인 포함, 모두 ACTIVE) 노출)
 */
data class SentInvitationResponse(
	val teamId: Long,
	val name: String,
	val regionId: Long,
	/** 활동지역 표시 문자열(시/도 시/군/구). regions join으로 채워진다. */
	val activityArea: String?,
	val introduction: String?,
	val status: TeamStatus,
	val members: List<Member>,
) {

	/** 구성원 항목(INVITING이면 초대 대상(INVITED), ACTIVE면 팀원 전원). 닉네임·직업·회사명·성별·프로필이미지를 담는다. */
	data class Member(
		val userId: Long,
		val nickname: String,
		val job: String?,
		val companyName: String?,
		val gender: Gender,
		val profileImageCode: String,
		val age: Int,
		val status: TeamMemberStatus,
	)

	companion object {
		/** 초대 현황이 없으면(null) null을 그대로 돌려준다. (진행 중인 초대 없음 = data:null) */
		fun of(invitation: SentInvitation?, today: LocalDate): SentInvitationResponse? =
			invitation?.let {
				SentInvitationResponse(
					teamId = invitation.teamId,
					name = invitation.name,
					regionId = invitation.regionId,
					activityArea = invitation.activityArea,
					introduction = invitation.introduction,
					status = invitation.status,
					members = invitation.members
						// INVITING 팀이면 수락 대기 중인 초대 대상(INVITED)만 노출한다. (ACTIVE 팀은 전원 노출)
						.filter { member: SentInvitationMember ->
							invitation.status != TeamStatus.INVITING || member.status == TeamMemberStatus.INVITED
						}
						.map { member: SentInvitationMember ->
						Member(
							userId = member.userId,
							nickname = member.nickname,
							job = member.job,
							companyName = member.companyName,
							gender = member.gender,
							profileImageCode = member.profileImageCode,
							age = member.birthday.ageAt(today),
							status = member.status,
						)
					},
				)
			}
	}
}
