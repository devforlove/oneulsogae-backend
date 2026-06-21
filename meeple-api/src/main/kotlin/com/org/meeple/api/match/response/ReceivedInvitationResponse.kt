package com.org.meeple.api.match.response

import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.query.dto.ReceivedInvitation

/**
 * 내가 받은 초대 한 건 응답. 팀 메타와 초대자(owner) 프로필을 담는다.
 */
data class ReceivedInvitationResponse(
	val teamId: Long,
	val name: String,
	val introduction: String?,
	val inviter: Inviter,
) {

	/** 초대자(owner) 프로필. 닉네임·직업·회사명·성별·프로필이미지·나이. */
	data class Inviter(
		val userId: Long,
		val nickname: String,
		val job: String?,
		val companyName: String?,
		val gender: Gender,
		val profileImageCode: String,
		val age: Int,
	)

	companion object {
		fun of(invitation: ReceivedInvitation): ReceivedInvitationResponse =
			ReceivedInvitationResponse(
				teamId = invitation.teamId,
				name = invitation.name,
				introduction = invitation.introduction,
				inviter = Inviter(
					userId = invitation.inviter.userId,
					nickname = invitation.inviter.nickname,
					job = invitation.inviter.job,
					companyName = invitation.inviter.companyName,
					gender = invitation.inviter.gender,
					profileImageCode = invitation.inviter.profileImageCode,
					age = invitation.inviter.age,
				),
			)
	}
}
