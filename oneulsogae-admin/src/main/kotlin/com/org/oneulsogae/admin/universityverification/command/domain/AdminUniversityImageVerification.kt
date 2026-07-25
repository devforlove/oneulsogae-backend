package com.org.oneulsogae.admin.universityverification.command.domain

import com.org.oneulsogae.common.user.UniversityImageVerificationStatus

/**
 * 어드민 심사용 학교 이미지 인증 도메인 모델(최소). 상태 전이(승인/반려)와 반려 사유를 캡슐화한다.
 * (admin은 core에 의존하지 않으므로 core UniversityImageVerification을 쓰지 않고 심사에 필요한 최소 필드만 둔다)
 */
data class AdminUniversityImageVerification(
	val id: Long,
	val userId: Long,
	val status: UniversityImageVerificationStatus,
	val rejectionReason: String? = null,
) {
	/** 승인. 이전에 반려로 남았을 수 있는 사유를 초기화한다. */
	fun approve(): AdminUniversityImageVerification =
		copy(status = UniversityImageVerificationStatus.APPROVED, rejectionReason = null)

	/** 반려. 사유([reason], 선택)를 함께 남긴다. */
	fun reject(reason: String?): AdminUniversityImageVerification =
		copy(status = UniversityImageVerificationStatus.REJECTED, rejectionReason = reason)
}
