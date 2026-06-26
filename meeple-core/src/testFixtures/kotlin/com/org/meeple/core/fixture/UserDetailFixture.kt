package com.org.meeple.core.fixture

import com.org.meeple.common.user.BodyType
import com.org.meeple.common.user.DrinkingStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.common.user.Religion
import com.org.meeple.common.user.SmokingStatus
import com.org.meeple.core.user.command.domain.UserDetail
import java.time.LocalDate

/**
 * [UserDetail] 도메인 모델 테스트 픽스처.
 * 기본은 매칭에 필요한 필드(성별·활동지역·결혼여부 등)가 모두 채워진 완성 프로필이다.
 */
object UserDetailFixture {

	fun create(
		id: Long = 0,
		userId: Long = 1L,
		nickname: String? = "테스트유저",
		profileImageCode: String? = "1",
		birthday: LocalDate? = LocalDate.of(1995, 1, 1),
		height: Int? = 175,
		gender: Gender? = Gender.MALE,
		phoneNumber: String? = "010-1234-5678",
		job: String? = "개발자",
		regionId: Long? = 1L,
		introduction: String? = "안녕하세요",
		traits: List<String> = listOf("성실함"),
		interests: List<String> = listOf("영화"),
		companyEmail: String? = "user@meeple.com",
		companyName: String? = "미플",
		maritalStatus: MaritalStatus? = MaritalStatus.SINGLE,
		smokingStatus: SmokingStatus? = SmokingStatus.NON_SMOKER,
		religion: Religion? = Religion.NONE,
		drinkingStatus: DrinkingStatus? = DrinkingStatus.SOMETIMES,
		bodyType: BodyType? = BodyType.MALE_NORMAL,
	): UserDetail =
		UserDetail(
			id = id,
			userId = userId,
			nickname = nickname,
			profileImageCode = profileImageCode,
			birthday = birthday,
			height = height,
			gender = gender,
			phoneNumber = phoneNumber,
			job = job,
			regionId = regionId,
			introduction = introduction,
			traits = traits,
			interests = interests,
			companyEmail = companyEmail,
			companyName = companyName,
			maritalStatus = maritalStatus,
			smokingStatus = smokingStatus,
			religion = religion,
			drinkingStatus = drinkingStatus,
			bodyType = bodyType,
		)
}
