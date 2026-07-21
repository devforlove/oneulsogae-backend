package com.org.oneulsogae.api.user.response

import com.org.oneulsogae.common.user.BodyType
import com.org.oneulsogae.common.user.DrinkingStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.common.user.MaritalStatus
import com.org.oneulsogae.common.user.Religion
import com.org.oneulsogae.common.user.SmokingStatus
import com.org.oneulsogae.core.common.time.ageAt
import com.org.oneulsogae.core.user.query.dto.UserDetailView
import java.time.LocalDate

/**
 * 다른 사용자의 프로필 응답. (공개 항목만)
 *
 * 본인 프로필([UserProfileResponse])과 달리 **연락처·인증 이메일은 싣지 않는다.**
 * 제외 항목과 이유:
 * - `phoneNumber`·`companyEmail`·`universityEmail`·`secondaryEmail`: 연락처는 프로필 열람만으로 노출할 값이 아니다.
 *   (인증 여부는 [companyName]·[universityName]으로 충분히 드러난다)
 * - `regionId`·`refuseSameCompanyIntro`: 본인 편집 화면 전용값이라 남에게는 의미가 없다.
 *
 * 노출 항목은 라운지 셀소 상세·대화 신청 목록이 이미 보여주는 범위와 같은 성격이다.
 */
data class PublicUserProfileResponse(
	val userId: Long,
	val nickname: String?,
	val profileImageCode: String?,
	val age: Int?,
	val height: Int?,
	val gender: Gender?,
	val job: String?,
	/** 활동지역 표시 문자열(시/도 시/군/구). 지역 미설정이면 null. */
	val activityArea: String?,
	val introduction: String?,
	val traits: List<String>,
	val interests: List<String>,
	val companyName: String?,
	val universityName: String?,
	val maritalStatus: MaritalStatus?,
	val smokingStatus: SmokingStatus?,
	val religion: Religion?,
	val drinkingStatus: DrinkingStatus?,
	val bodyType: BodyType?,
) {
	companion object {

		fun of(detail: UserDetailView, today: LocalDate): PublicUserProfileResponse =
			PublicUserProfileResponse(
				userId = detail.userId,
				nickname = detail.nickname,
				profileImageCode = detail.profileImageCode,
				age = detail.birthday?.ageAt(today),
				height = detail.height,
				gender = detail.gender,
				job = detail.job,
				activityArea = detail.activityArea,
				introduction = detail.introduction,
				traits = detail.traits,
				interests = detail.interests,
				companyName = detail.companyName,
				universityName = detail.universityName,
				maritalStatus = detail.maritalStatus,
				smokingStatus = detail.smokingStatus,
				religion = detail.religion,
				drinkingStatus = detail.drinkingStatus,
				bodyType = detail.bodyType,
			)
	}
}
