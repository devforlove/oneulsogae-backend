package com.org.oneulsogae.api.user.response

import com.org.oneulsogae.common.user.BodyType
import com.org.oneulsogae.common.user.DrinkingStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.common.user.MaritalStatus
import com.org.oneulsogae.common.user.Religion
import com.org.oneulsogae.common.user.SmokingStatus
import com.org.oneulsogae.core.common.time.ageAt
import com.org.oneulsogae.core.user.command.domain.UserDetail
import com.org.oneulsogae.core.user.query.dto.UserDetailView
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
	/** 보조 이메일. 마케팅·광고·매칭 알림 수신용. (미설정 시 null) */
	val secondaryEmail: String?,
	val maritalStatus: MaritalStatus?,
	val smokingStatus: SmokingStatus?,
	val religion: Religion?,
	val drinkingStatus: DrinkingStatus?,
	val bodyType: BodyType?,
	/** 같은 회사 구성원 소개 거부 여부. 조회(GET) 시 match_user join으로 채워지고, 수정(PUT) 응답에선 null. */
	val refuseSameCompanyIntro: Boolean?,
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
				secondaryEmail = detail.secondaryEmail,
				maritalStatus = detail.maritalStatus,
				smokingStatus = detail.smokingStatus,
				religion = detail.religion,
				drinkingStatus = detail.drinkingStatus,
				bodyType = detail.bodyType,
				refuseSameCompanyIntro = detail.refuseSameCompanyIntro,
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
				secondaryEmail = detail.secondaryEmail,
				maritalStatus = detail.maritalStatus,
				smokingStatus = detail.smokingStatus,
				religion = detail.religion,
				drinkingStatus = detail.drinkingStatus,
				bodyType = detail.bodyType,
				refuseSameCompanyIntro = null,
			)
	}
}
