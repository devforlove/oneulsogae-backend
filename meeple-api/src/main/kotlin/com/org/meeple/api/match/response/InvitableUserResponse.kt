package com.org.meeple.api.match.response

import com.org.meeple.common.user.Gender
import com.org.meeple.core.common.time.ageAt
import com.org.meeple.core.teammatch.query.dto.InvitableUser
import java.time.LocalDate

/**
 * 초대 가능 유저 검색 결과 항목 응답. 식별자·닉네임·직업·회사명·성별·프로필이미지·나이를 담는다.
 */
data class InvitableUserResponse(
	val userId: Long,
	val nickname: String,
	val job: String?,
	val companyName: String?,
	val universityName: String?,
	val gender: Gender,
	val profileImageCode: String,
	val age: Int,
) {
	companion object {
		fun of(user: InvitableUser, today: LocalDate): InvitableUserResponse =
			InvitableUserResponse(
				userId = user.userId,
				nickname = user.nickname,
				job = user.job,
				companyName = user.companyName,
				universityName = user.universityName,
				gender = user.gender,
				profileImageCode = user.profileImageCode,
				age = user.birthday.ageAt(today),
			)

		/** 초대 가능 유저 목록을 응답 목록으로 변환한다. */
		fun listOf(users: List<InvitableUser>, today: LocalDate): List<InvitableUserResponse> =
			users.map { of(it, today) }
	}
}
