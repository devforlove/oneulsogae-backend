package com.org.oneulsogae.admin.memberverification.command.domain

import com.org.oneulsogae.common.gathering.MemberVerificationStatus

/**
 * 어드민 심사용 멤버 인증 도메인 모델(최소). 상태 전이(승인)만 캡슐화한다.
 * (admin은 core에 의존하지 않으므로 core 도메인을 쓰지 않고 심사에 필요한 최소 필드만 둔다)
 */
data class AdminMemberVerification(
	val id: Long,
	val userId: Long,
	val status: MemberVerificationStatus,
	val rejectionReason: String? = null,
) {
	/** 승인. 이전에 반려로 남았을 수 있는 사유를 초기화한다. */
	fun approve(): AdminMemberVerification =
		copy(status = MemberVerificationStatus.APPROVED, rejectionReason = null)

	/** 반려. 사유([reason], 선택)를 함께 남긴다. */
	fun reject(reason: String?): AdminMemberVerification =
		copy(status = MemberVerificationStatus.REJECTED, rejectionReason = reason)
}
