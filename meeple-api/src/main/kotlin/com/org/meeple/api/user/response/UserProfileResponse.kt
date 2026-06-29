package com.org.meeple.api.user.response

import com.org.meeple.common.user.BodyType
import com.org.meeple.common.user.DrinkingStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.common.user.Religion
import com.org.meeple.common.user.SmokingStatus
import com.org.meeple.core.common.time.ageAt
import com.org.meeple.core.user.command.domain.UserDetail
import com.org.meeple.core.user.query.dto.UserDetailView
import java.time.LocalDate

/** 사용자 프로필 응답. 프로필 조회/수정 엔드포인트에서 사용한다. */
data class UserProfileResponse(
	val nickname: String?,
	val profileImageCode: String?,
	val age: Int?,
	val height: Int?,
	val gender: Gender?,
	val phoneNumber: String?,
	val job: String?,
	/** 활동지역 id(regions FK). 편집 화면에서 현재 지역 선택값으로 쓴다. */
	val regionId: Long?,
	/** 활동지역 표시 문자열(시/도 시/군/구). 조회(GET) 시 regions join으로 채워지고, 수정(PUT) 응답에선 null. */
	val activityArea: String?,
	val introduction: String?,
	val traits: List<String>,
	val interests: List<String>,
	val companyEmail: String?,
	val companyName: String?,
	val universityEmail: String?,
	val universityName: String?,
	val maritalStatus: MaritalStatus?,
	val smokingStatus: SmokingStatus?,
	val religion: Religion?,
	val drinkingStatus: DrinkingStatus?,
	val bodyType: BodyType?,
) {
	companion object {
		/** 조회(query) read model 매핑. */
		fun of(detail: UserDetailView, today: LocalDate): UserProfileResponse =
			UserProfileResponse(
				nickname = detail.nickname,
				profileImageCode = detail.profileImageCode,
				age = detail.birthday?.ageAt(today),
				height = detail.height,
				gender = detail.gender,
				phoneNumber = detail.phoneNumber,
				job = detail.job,
				regionId = detail.regionId,
				activityArea = detail.activityArea,
				introduction = detail.introduction,
				traits = detail.traits,
				interests = detail.interests,
				companyEmail = detail.companyEmail,
				companyName = detail.companyName,
				universityEmail = detail.universityEmail,
				universityName = detail.universityName,
				maritalStatus = detail.maritalStatus,
				smokingStatus = detail.smokingStatus,
				religion = detail.religion,
				drinkingStatus = detail.drinkingStatus,
				bodyType = detail.bodyType,
			)

		/** 명령(command) 결과 도메인 매핑. (프로필 수정 후 갱신된 [UserDetail] 렌더링 — 표시용 활동지역은 join이 없어 null, regionId만 싣는다) */
		fun of(detail: UserDetail, today: LocalDate): UserProfileResponse =
			UserProfileResponse(
				nickname = detail.nickname,
				profileImageCode = detail.profileImageCode,
				age = detail.age(today),
				height = detail.height,
				gender = detail.gender,
				phoneNumber = detail.phoneNumber,
				job = detail.job,
				regionId = detail.regionId,
				activityArea = null,
				introduction = detail.introduction,
				traits = detail.traits,
				interests = detail.interests,
				companyEmail = detail.companyEmail,
				companyName = detail.companyName,
				universityEmail = detail.universityEmail,
				universityName = detail.universityName,
				maritalStatus = detail.maritalStatus,
				smokingStatus = detail.smokingStatus,
				religion = detail.religion,
				drinkingStatus = detail.drinkingStatus,
				bodyType = detail.bodyType,
			)
	}
}
