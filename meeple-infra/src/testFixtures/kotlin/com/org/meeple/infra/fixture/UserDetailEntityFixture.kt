package com.org.meeple.infra.fixture

import com.org.meeple.common.user.Gender
import com.org.meeple.infra.user.entity.UserDetailEntity

/**
 * [UserDetailEntity] 테스트 픽스처. 매칭 응답 결과에 노출되는 최소 프로필(닉네임·성별·나이)을 기본으로 채운다.
 * 나머지 상세 항목은 null/빈 값 기본을 따른다.
 */
object UserDetailEntityFixture {

	fun create(
		userId: Long = 1L,
		nickname: String? = "테스트유저",
		profileImageCode: String? = null,
		gender: Gender? = Gender.FEMALE,
		age: Int? = 28,
		height: Int? = null,
		job: String? = null,
		activityArea: String? = null,
		regionCode: Int? = null,
		introduction: String? = null,
		companyName: String? = null,
	): UserDetailEntity =
		UserDetailEntity(
			userId = userId,
			nickname = nickname,
			profileImageCode = profileImageCode,
			gender = gender,
			age = age,
			height = height,
			job = job,
			activityArea = activityArea,
			regionCode = regionCode,
			introduction = introduction,
			companyName = companyName,
		)
}
