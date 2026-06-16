package com.org.meeple.api.user.response

import com.org.meeple.common.user.BodyType
import com.org.meeple.common.user.DrinkingStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.common.user.Religion
import com.org.meeple.common.user.SmokingStatus
import com.org.meeple.core.user.command.domain.UserDetail
import com.org.meeple.core.user.query.dto.UserDetailView

/** 사용자 프로필 응답. 프로필 조회/수정 엔드포인트에서 사용한다. */
data class UserProfileResponse(
	val nickname: String?,
	val profileImageCode: String?,
	val age: Int?,
	val height: Int?,
	val gender: Gender?,
	val phoneNumber: String?,
	val job: String?,
	val activityArea: String?,
	val introduction: String?,
	val traits: List<String>,
	val interests: List<String>,
	val companyEmail: String?,
	val companyName: String?,
	val maritalStatus: MaritalStatus?,
	val smokingStatus: SmokingStatus?,
	val religion: Religion?,
	val drinkingStatus: DrinkingStatus?,
	val bodyType: BodyType?,
) {
	companion object {
		/** 조회(query) read model 매핑. */
		fun of(detail: UserDetailView): UserProfileResponse =
			UserProfileResponse(
				nickname = detail.nickname,
				profileImageCode = detail.profileImageCode,
				age = detail.age,
				height = detail.height,
				gender = detail.gender,
				phoneNumber = detail.phoneNumber,
				job = detail.job,
				activityArea = detail.activityArea,
				introduction = detail.introduction,
				traits = detail.traits,
				interests = detail.interests,
				companyEmail = detail.companyEmail,
				companyName = detail.companyName,
				maritalStatus = detail.maritalStatus,
				smokingStatus = detail.smokingStatus,
				religion = detail.religion,
				drinkingStatus = detail.drinkingStatus,
				bodyType = detail.bodyType,
			)

		/** 명령(command) 결과 도메인 매핑. (프로필 수정 후 갱신된 [UserDetail] 렌더링) */
		fun of(detail: UserDetail): UserProfileResponse =
			UserProfileResponse(
				nickname = detail.nickname,
				profileImageCode = detail.profileImageCode,
				age = detail.age,
				height = detail.height,
				gender = detail.gender,
				phoneNumber = detail.phoneNumber,
				job = detail.job,
				activityArea = detail.activityArea,
				introduction = detail.introduction,
				traits = detail.traits,
				interests = detail.interests,
				companyEmail = detail.companyEmail,
				companyName = detail.companyName,
				maritalStatus = detail.maritalStatus,
				smokingStatus = detail.smokingStatus,
				religion = detail.religion,
				drinkingStatus = detail.drinkingStatus,
				bodyType = detail.bodyType,
			)
	}
}
