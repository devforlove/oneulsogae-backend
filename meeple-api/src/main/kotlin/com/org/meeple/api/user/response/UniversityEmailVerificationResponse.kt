package com.org.meeple.api.user.response

import com.org.meeple.core.user.command.domain.UniversityEmailVerification
import java.time.LocalDateTime

/** 학교 이메일 인증 요청 결과 응답. 발송 대상 이메일과 인증 만료 시각을 알린다. */
data class UniversityEmailVerificationResponse(
	val universityEmail: String,
	val expiresAt: LocalDateTime,
) {
	companion object {
		fun of(verification: UniversityEmailVerification): UniversityEmailVerificationResponse =
			UniversityEmailVerificationResponse(
				universityEmail = verification.universityEmail,
				expiresAt = verification.expiresAt,
			)
	}
}
