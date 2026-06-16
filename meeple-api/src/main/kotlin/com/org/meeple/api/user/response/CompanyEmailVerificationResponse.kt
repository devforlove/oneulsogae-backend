package com.org.meeple.api.user.response

import com.org.meeple.core.user.command.domain.CompanyEmailVerification
import java.time.LocalDateTime

/** 회사 이메일 인증 요청 결과 응답. 발송 대상 이메일과 인증 만료 시각을 알린다. */
data class CompanyEmailVerificationResponse(
	val companyEmail: String,
	val expiresAt: LocalDateTime,
) {
	companion object {
		fun of(verification: CompanyEmailVerification): CompanyEmailVerificationResponse =
			CompanyEmailVerificationResponse(
				companyEmail = verification.companyEmail,
				expiresAt = verification.expiresAt,
			)
	}
}
