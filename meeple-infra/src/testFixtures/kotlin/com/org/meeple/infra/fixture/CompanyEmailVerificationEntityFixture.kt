package com.org.meeple.infra.fixture

import com.org.meeple.infra.user.command.entity.CompanyEmailVerificationEntity
import java.time.LocalDateTime

/**
 * [CompanyEmailVerificationEntity] 테스트 픽스처.
 * 기본은 아직 검증되지 않은(verifiedAt=null) 유효한 인증 요청이다. 만료 시각을 충분히 미래로 둬 만료 검사를 통과한다.
 */
object CompanyEmailVerificationEntityFixture {

	fun create(
		userId: Long = 1L,
		companyEmail: String = "user@meeple.com",
		code: String = "123456",
		expiresAt: LocalDateTime = LocalDateTime.now().plusMinutes(10),
		verifiedAt: LocalDateTime? = null,
	): CompanyEmailVerificationEntity =
		CompanyEmailVerificationEntity(
			userId = userId,
			companyEmail = companyEmail,
			code = code,
			expiresAt = expiresAt,
			verifiedAt = verifiedAt,
		)
}
