package com.org.oneulsogae.api.user.response

import com.org.oneulsogae.common.user.UniversityImageVerificationStatus
import com.org.oneulsogae.core.user.command.domain.UniversityImageVerification

/**
 * 학교 서류 이미지 인증 제출 응답.
 * 서류는 비공개 저장이라 파일 URL 대신 인증 식별자와 심사 상태(PENDING)만 내려준다.
 */
data class UniversityImageVerificationResponse(
	val verificationId: Long,
	val status: UniversityImageVerificationStatus,
) {
	companion object {

		fun of(verification: UniversityImageVerification): UniversityImageVerificationResponse =
			UniversityImageVerificationResponse(
				verificationId = verification.id,
				status = verification.status,
			)
	}
}
