package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.infra.user.command.entity.UserCompanyEntity

/**
 * [UserCompanyEntity] 테스트 픽스처. 회사 이메일 도메인 -> 회사명 매핑을 만든다.
 * (email_domain 유니크 제약이 있어 한 테스트에서 여러 매핑을 만들면 emailDomain을 달리한다.)
 */
object UserCompanyEntityFixture {

	fun create(
		emailDomain: String = "oneulsogae.com",
		companyName: String = "오늘의 소개",
	): UserCompanyEntity =
		UserCompanyEntity(
			emailDomain = emailDomain,
			companyName = companyName,
		)
}
