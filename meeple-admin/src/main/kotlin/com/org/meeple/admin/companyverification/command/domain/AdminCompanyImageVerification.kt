package com.org.meeple.admin.companyverification.command.domain

import com.org.meeple.common.user.CompanyImageVerificationStatus

/**
 * 어드민 심사용 회사 이미지 인증 도메인 모델(최소). 상태 전이(승인/반려)를 캡슐화한다.
 * (admin은 core에 의존하지 않으므로 core CompanyImageVerification을 쓰지 않고 심사에 필요한 최소 필드만 둔다)
 */
data class AdminCompanyImageVerification(
	val id: Long,
	val userId: Long,
	val status: CompanyImageVerificationStatus,
) {
	fun approve(): AdminCompanyImageVerification = copy(status = CompanyImageVerificationStatus.APPROVED)

	fun reject(): AdminCompanyImageVerification = copy(status = CompanyImageVerificationStatus.REJECTED)
}
