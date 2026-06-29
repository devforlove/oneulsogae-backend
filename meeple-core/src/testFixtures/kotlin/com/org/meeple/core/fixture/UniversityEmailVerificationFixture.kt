package com.org.meeple.core.fixture

import com.org.meeple.core.user.command.domain.UniversityEmailVerification
import java.time.LocalDateTime

/** [UniversityEmailVerification] 도메인 모델 테스트 픽스처. 기본은 아직 검증되지 않은(verifiedAt=null) 학교 도메인 인증 요청이다. */
object UniversityEmailVerificationFixture {

	fun create(
		id: Long = 0,
		userId: Long = 1L,
		universityEmail: String = "student@snu.ac.kr",
		code: String = "123456",
		expiresAt: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 10),
		verifiedAt: LocalDateTime? = null,
	): UniversityEmailVerification =
		UniversityEmailVerification(
			id = id,
			userId = userId,
			universityEmail = universityEmail,
			code = code,
			expiresAt = expiresAt,
			verifiedAt = verifiedAt,
		)
}
