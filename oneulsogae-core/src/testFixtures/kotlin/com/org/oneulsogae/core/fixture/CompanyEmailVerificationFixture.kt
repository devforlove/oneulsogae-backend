package com.org.oneulsogae.core.fixture

import com.org.oneulsogae.core.user.command.domain.CompanyEmailVerification
import java.time.LocalDateTime

/** [CompanyEmailVerification] 도메인 모델 테스트 픽스처. 기본은 아직 검증되지 않은(verifiedAt=null) 회사 도메인 인증 요청이다. */
object CompanyEmailVerificationFixture {

	fun create(
		id: Long = 0,
		userId: Long = 1L,
		companyEmail: String = "user@oneulsogae.com",
		code: String = "123456",
		expiresAt: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 10),
		verifiedAt: LocalDateTime? = null,
	): CompanyEmailVerification =
		CompanyEmailVerification(
			id = id,
			userId = userId,
			companyEmail = companyEmail,
			code = code,
			expiresAt = expiresAt,
			verifiedAt = verifiedAt,
		)
}
