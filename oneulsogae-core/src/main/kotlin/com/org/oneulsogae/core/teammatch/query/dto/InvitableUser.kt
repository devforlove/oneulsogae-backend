package com.org.oneulsogae.core.teammatch.query.dto

import com.org.oneulsogae.common.user.Gender
import java.time.LocalDate

/**
 * 초대 가능한 유저 검색 결과(read model). 초대 UI에 노출할 식별자·닉네임·직업·회사명·성별·프로필이미지·생년월일을 담는다.
 * query 전용 view이며 command 도메인을 참조하지 않는다.
 */
data class InvitableUser(
	val userId: Long,
	val nickname: String,
	val job: String?,
	val companyName: String?,
	val universityName: String?,
	val gender: Gender,
	val profileImageCode: String,
	val birthday: LocalDate,
)
