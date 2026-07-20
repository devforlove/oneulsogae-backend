package com.org.oneulsogae.core.user.command.domain

/** 본인확인 거래 상태. */
enum class IdentityVerificationStatus {
	/** 거래등록만 완료(인증창 호출 전/후, 결과 확정 전). */
	REQUESTED,

	/** 결과 확정·검증 완료. */
	VERIFIED,

	/** 결과 확정 실패(성인 아님 등). */
	FAILED,
}
