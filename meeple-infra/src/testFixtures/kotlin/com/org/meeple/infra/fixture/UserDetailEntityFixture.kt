package com.org.meeple.infra.fixture

import com.org.meeple.common.user.Gender
import com.org.meeple.infra.user.command.entity.UserDetailEntity
import java.time.LocalDate

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
		birthday: LocalDate? = LocalDate.of(1996, 1, 1),
		height: Int? = null,
		job: String? = null,
		regionId: Long? = null,
		introduction: String? = null,
		companyName: String? = null,
	): UserDetailEntity =
		UserDetailEntity(
			userId = userId,
			nickname = nickname,
			profileImageCode = profileImageCode,
			gender = gender,
			birthday = birthday,
			height = height,
			job = job,
			regionId = regionId,
			introduction = introduction,
			companyName = companyName,
		)
}
