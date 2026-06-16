package com.org.meeple.core.user.query.dto

import com.org.meeple.common.user.BodyType
import com.org.meeple.common.user.DrinkingStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.common.user.Religion
import com.org.meeple.common.user.SmokingStatus

/**
 * 사용자 프로필 상세 조회 결과(read model). query는 command 도메인([com.org.meeple.core.user.command.domain.UserDetail]) 대신 이 view를 쓴다.
 */
data class UserDetailView(
	val id: Long,
	val userId: Long,
	val nickname: String?,
	val profileImageCode: String?,
	val age: Int?,
	val height: Int?,
	val gender: Gender?,
	val phoneNumber: String?,
	val job: String?,
	val activityArea: String?,
	val regionCode: Int?,
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
)
