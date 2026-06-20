package com.org.meeple.api.match.response

import com.org.meeple.core.match.query.dto.InvitableUser

/**
 * 초대 가능 유저 검색 결과 항목 응답. 식별자·닉네임·직업·회사명을 담는다.
 */
data class InvitableUserResponse(
	val userId: Long,
	val nickname: String,
	val job: String?,
	val companyName: String?,
) {
	companion object {
		fun of(user: InvitableUser): InvitableUserResponse =
			InvitableUserResponse(
				userId = user.userId,
				nickname = user.nickname,
				job = user.job,
				companyName = user.companyName,
			)
	}
}
