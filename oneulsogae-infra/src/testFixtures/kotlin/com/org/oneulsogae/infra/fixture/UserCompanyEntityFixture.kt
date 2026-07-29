package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.infra.user.command.entity.UserCompanyEntity

/**
 * [UserCompanyEntity] 테스트 픽스처. 회사 이메일 도메인 -> 회사명 매핑을 만든다.
 * ((email_domain, company_name) 조합 유니크라 같은 도메인에 회사명을 달리해 여러 회사를 만들 수 있다.)
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
