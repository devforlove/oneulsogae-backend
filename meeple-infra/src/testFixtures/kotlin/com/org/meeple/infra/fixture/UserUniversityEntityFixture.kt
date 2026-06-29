package com.org.meeple.infra.fixture

import com.org.meeple.infra.user.command.entity.UserUniversityEntity

/**
 * [UserUniversityEntity] 테스트 픽스처. 학교 이메일 도메인 -> 학교명 매핑을 만든다.
 * (email_domain 유니크 제약이 있어 한 테스트에서 여러 매핑을 만들면 emailDomain을 달리한다.)
 */
object UserUniversityEntityFixture {

	fun create(
		emailDomain: String = "snu.ac.kr",
		universityName: String = "서울대학교",
	): UserUniversityEntity =
		UserUniversityEntity(
			emailDomain = emailDomain,
			universityName = universityName,
		)
}
