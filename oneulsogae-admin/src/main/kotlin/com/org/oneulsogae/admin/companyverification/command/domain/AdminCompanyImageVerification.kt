package com.org.oneulsogae.admin.companyverification.command.domain

import com.org.oneulsogae.common.user.CompanyImageVerificationStatus

/**
 * 어드민 심사용 회사 이미지 인증 도메인 모델(최소). 상태 전이(승인/반려)와 반려 사유를 캡슐화한다.
 * (admin은 core에 의존하지 않으므로 core CompanyImageVerification을 쓰지 않고 심사에 필요한 최소 필드만 둔다)
 */
data class AdminCompanyImageVerification(
	val id: Long,
	val userId: Long,
	val status: CompanyImageVerificationStatus,
	val rejectionReason: String? = null,
) {
	/** 승인. 이전에 반려로 남았을 수 있는 사유를 초기화한다. */
	fun approve(): AdminCompanyImageVerification =
		copy(status = CompanyImageVerificationStatus.APPROVED, rejectionReason = null)

	/** 반려. 사유([reason], 선택)를 함께 남긴다. */
	fun reject(reason: String?): AdminCompanyImageVerification =
		copy(status = CompanyImageVerificationStatus.REJECTED, rejectionReason = reason)
}
