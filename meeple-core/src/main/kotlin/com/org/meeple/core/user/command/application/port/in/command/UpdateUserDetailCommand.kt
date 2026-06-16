package com.org.meeple.core.user.command.application.port.`in`.command

import com.org.meeple.common.user.BodyType
import com.org.meeple.common.user.DrinkingStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.common.user.Religion
import com.org.meeple.common.user.SmokingStatus

/**
 * 프로필에서 사용자가 직접 편집 가능한 필드 묶음.
 * 식별/소유(id, userId), 서버 배정값(profileImageCode), 회사명(companyName)은 제외한다.
 * 온보딩 프로필 입력은 모든 필드가 필수이며, 진입점(요청 DTO의 Bean Validation)에서 null·빈 값을 거른 뒤 채워진다.
 * 따라서 이 명령의 필드는 모두 non-null이다. (단, 활동지역의 권역 인식은 도메인에서 별도로 검증한다)
 */
data class UpdateUserDetailCommand(
	val nickname: String,
	val age: Int,
	val height: Int,
	val gender: Gender,
	val phoneNumber: String,
	val job: String,
	val activityArea: String,
	val introduction: String,
	val traits: List<String>,
	val interests: List<String>,
	val companyEmail: String,
	val maritalStatus: MaritalStatus,
	val smokingStatus: SmokingStatus,
	val religion: Religion,
	val drinkingStatus: DrinkingStatus,
	val bodyType: BodyType,
)
