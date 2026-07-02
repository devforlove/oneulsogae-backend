package com.org.meeple.infra.fixture

import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.infra.matchuser.command.entity.MatchUserEntity
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [MatchUserEntity](매칭 읽기 모델) 테스트 픽스처. 후보 선정에 쓰는 기준 필드를 합리적 기본값으로 채운다.
 * 후보 조회는 성별·권역·최근 로그인으로 거르므로, 테스트에서 이 값들만 덮어쓰면 후보 풀을 구성할 수 있다.
 * (last_login_at 기본값은 최근으로 둬, 최근 로그인 필터에 걸리도록 한다)
 */
object MatchUserEntityFixture {

	fun create(
		userId: Long = 1L,
		gender: Gender = Gender.FEMALE,
		regionId: Long = 1L,
		maritalStatus: MaritalStatus = MaritalStatus.SINGLE,
		birthday: LocalDate = LocalDate.of(1996, 1, 1),
		nickname: String = "테스트유저",
		profileImageCode: String = "1",
		lastLoginAt: LocalDateTime = LocalDateTime.now(),
		companyName: String? = null,
		refuseSameCompanyIntro: Boolean = true,
	): MatchUserEntity =
		MatchUserEntity(
			userId = userId,
			gender = gender,
			regionId = regionId,
			maritalStatus = maritalStatus,
			birthday = birthday,
			nickname = nickname,
			profileImageCode = profileImageCode,
			lastLoginAt = lastLoginAt,
			companyName = companyName,
			refuseSameCompanyIntro = refuseSameCompanyIntro,
		)
}
