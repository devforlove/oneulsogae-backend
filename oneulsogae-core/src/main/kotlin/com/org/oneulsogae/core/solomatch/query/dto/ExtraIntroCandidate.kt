package com.org.oneulsogae.core.solomatch.query.dto

import com.org.oneulsogae.common.user.BodyType
import com.org.oneulsogae.common.user.DrinkingStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.common.user.MaritalStatus
import com.org.oneulsogae.common.user.Religion
import com.org.oneulsogae.common.user.SmokingStatus
import java.time.LocalDate

/** 추가 소개 후보 표시용 read model. (닉네임 블러 등 노출 정책은 프론트가 처리) */
data class ExtraIntroCandidate(
	val userId: Long,
	val nickname: String?,
	val profileImageCode: String?,
	val birthday: LocalDate?,
	val height: Int?,
	val gender: Gender?,
	val job: String?,
	val activityArea: String?,
	val introduction: String?,
	val companyName: String?,
	val universityName: String?,
	val traits: List<String>,
	val interests: List<String>,
	val maritalStatus: MaritalStatus?,
	val smokingStatus: SmokingStatus?,
	val religion: Religion?,
	val drinkingStatus: DrinkingStatus?,
	val bodyType: BodyType?,
)
