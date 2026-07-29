package com.org.oneulsogae.api.user.response

import com.org.oneulsogae.core.user.command.application.port.`in`.result.RequestCompanyEmailVerificationResult
import com.org.oneulsogae.core.user.query.dto.UserCompany
import java.time.LocalDateTime

/**
 * 회사 이메일 인증 요청 결과 응답.
 * - 발송 완료: [expiresAt]에 인증 만료 시각이 담기고 [companyCandidates]는 null.
 * - 회사 선택 필요([requiresCompanySelection]=true): 발송 없이 [companyCandidates]에 같은 도메인 회사 후보가 담긴다.
 *   클라이언트는 후보 중 하나의 companyId를 지정해 재요청한다.
 */
data class CompanyEmailVerificationResponse(
	val companyEmail: String,
	val expiresAt: LocalDateTime?,
	val requiresCompanySelection: Boolean,
	val companyCandidates: List<CompanyCandidateResponse>?,
) {
	companion object {
		fun of(companyEmail: String, result: RequestCompanyEmailVerificationResult): CompanyEmailVerificationResponse =
			when (result) {
				is RequestCompanyEmailVerificationResult.Sent -> CompanyEmailVerificationResponse(
					companyEmail = result.verification.companyEmail,
					expiresAt = result.verification.expiresAt,
					requiresCompanySelection = false,
					companyCandidates = null,
				)
				is RequestCompanyEmailVerificationResult.CompanySelectionRequired -> CompanyEmailVerificationResponse(
					companyEmail = companyEmail,
					expiresAt = null,
					requiresCompanySelection = true,
					companyCandidates = result.candidates.map { candidate: UserCompany -> CompanyCandidateResponse.of(candidate) },
				)
			}
	}
}

/** 같은 도메인을 쓰는 회사 후보. 재요청 시 [companyId]를 지정한다. */
data class CompanyCandidateResponse(
	val companyId: Long,
	val companyName: String,
) {
	companion object {
		fun of(company: UserCompany): CompanyCandidateResponse =
			CompanyCandidateResponse(companyId = company.id, companyName = company.companyName)
	}
}
