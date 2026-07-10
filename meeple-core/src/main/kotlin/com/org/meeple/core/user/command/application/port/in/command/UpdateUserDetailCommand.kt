package com.org.meeple.core.user.command.application.port.`in`.command

import com.org.meeple.common.user.BodyType
import com.org.meeple.common.user.DrinkingStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.common.user.Religion
import com.org.meeple.common.user.SmokingStatus
import java.time.LocalDate

/**
 * 프로필에서 사용자가 직접 편집 가능한 필드 묶음.
 * 식별/소유(id, userId), 서버 배정값(profileImageCode), 회사명(companyName)은 제외한다.
 * 온보딩 프로필 입력은 대부분 필드가 필수이며, 진입점(요청 DTO의 Bean Validation)에서 null·빈 값을 거른 뒤 채워진다.
 * 단, birthday·gender·phoneNumber는 본인인증(KCP)이 이미 채워둔 값을 신뢰하는 필드라 nullable이다.
 * null이면 서비스가 기존 [UserDetail] 저장값으로 채운다. (활동지역은 regionId로 받고, 서비스가 region 도메인에서 활동지역 문자열로 해석한다)
 */
data class UpdateUserDetailCommand(
	val nickname: String,
	val birthday: LocalDate?,
	val height: Int,
	val gender: Gender?,
	val phoneNumber: String?,
	val job: String,
	val regionId: Long,
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
