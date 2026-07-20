package com.org.oneulsogae.api.user.response

import com.org.oneulsogae.common.user.CompanyImageVerificationStatus
import com.org.oneulsogae.core.user.command.domain.CompanyImageVerification

/**
 * 직장 서류 이미지 인증 제출 응답.
 * 서류는 비공개 저장이라 파일 URL 대신 인증 식별자와 심사 상태(PENDING)만 내려준다.
 */
data class CompanyImageVerificationResponse(
	val verificationId: Long,
	val status: CompanyImageVerificationStatus,
) {
	companion object {

		fun of(verification: CompanyImageVerification): CompanyImageVerificationResponse =
			CompanyImageVerificationResponse(
				verificationId = verification.id,
				status = verification.status,
			)
	}
}
