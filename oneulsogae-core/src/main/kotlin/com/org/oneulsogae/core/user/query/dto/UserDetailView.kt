package com.org.oneulsogae.core.user.query.dto

import com.org.oneulsogae.common.user.BodyType
import com.org.oneulsogae.common.user.DrinkingStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.common.user.MaritalStatus
import com.org.oneulsogae.common.user.Religion
import com.org.oneulsogae.common.user.SmokingStatus
import java.time.LocalDate

/**
 * 사용자 프로필 상세 조회 결과(read model). query는 command 도메인([com.org.oneulsogae.core.user.command.domain.UserDetail]) 대신 이 view를 쓴다.
 */
data class UserDetailView(
	val id: Long,
	val userId: Long,
	val nickname: String?,
	val profileImageCode: String?,
	val birthday: LocalDate?,
	val height: Int?,
	val gender: Gender?,
	val phoneNumber: String?,
	val job: String?,
	/** 활동지역 id(regions FK). 편집 화면에서 현재 지역 선택값으로 쓴다. */
	val regionId: Long?,
	/** 활동지역 표시 문자열(시/도 시/군/구). regions join으로 채운다. */
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
	/** 같은 회사 구성원 소개 거부 여부. match_user join으로 채우며, 행이 없으면 기본값 거부(true). */
	val refuseSameCompanyIntro: Boolean,
)
