package com.org.meeple.api.match.response

import com.org.meeple.common.match.TeamMemberStatus
import com.org.meeple.common.match.TeamStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.query.dto.SentInvitation
import com.org.meeple.core.match.query.dto.SentInvitationMember

/**
 * 내가 보낸 초대 현황 응답. 팀 식별자·이름·소개·상태와 초대받은(INVITED) 구성원의 프로필을 담는다.
 * (초대자 본인(ACTIVE)은 members에서 제외하고, 초대 대상만 노출한다)
 */
data class SentInvitationResponse(
	val teamId: Long,
	val name: String,
	val introduction: String?,
	val status: TeamStatus,
	val members: List<Member>,
) {

	/** 초대받은 구성원 항목(status=INVITED, 수락 대기 중인 초대 대상). 닉네임·직업·회사명·성별·프로필이미지를 담는다. */
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
		fun of(invitation: SentInvitation?): SentInvitationResponse? =
			invitation?.let {
				SentInvitationResponse(
					teamId = invitation.teamId,
					name = invitation.name,
					introduction = invitation.introduction,
					status = invitation.status,
					members = invitation.members
						.filter { member: SentInvitationMember -> member.status == TeamMemberStatus.INVITED }
						.map { member: SentInvitationMember ->
						Member(
							userId = member.userId,
							nickname = member.nickname,
							job = member.job,
							companyName = member.companyName,
							gender = member.gender,
							profileImageCode = member.profileImageCode,
							age = member.age,
							status = member.status,
						)
					},
				)
			}
	}
}
