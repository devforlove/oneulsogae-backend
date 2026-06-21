package com.org.meeple.core.match.query.dto

import com.org.meeple.common.user.Gender

/**
 * 내가 받은 초대 한 건(read model). 초대받은(INVITED) 유저가 보는, 대기 중(INVITING) 팀과 초대자 프로필.
 * query 전용 view이며 command 도메인을 참조하지 않는다.
 */
data class ReceivedInvitation(
	val teamId: Long,
	val name: String,
	val introduction: String?,
	val inviter: ReceivedInvitationInviter,
)

/** 초대자(owner) 프로필. 닉네임·프로필이미지·나이는 match_user, 직업·회사명은 user_details에서 온다. */
data class ReceivedInvitationInviter(
	val userId: Long,
	val nickname: String,
	val job: String?,
	val companyName: String?,
	val gender: Gender,
	val profileImageCode: String,
	val age: Int,
)
