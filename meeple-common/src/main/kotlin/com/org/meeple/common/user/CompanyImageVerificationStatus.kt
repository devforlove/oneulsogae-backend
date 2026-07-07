package com.org.meeple.common.user

/**
 * 직장 서류 이미지 인증의 심사 상태.
 * 서류는 자동 검증이 불가능해 사람(어드민)이 확인해야 하므로, 제출 시 [PENDING]으로 시작한다.
 */
enum class CompanyImageVerificationStatus(val description: String) {

	/** 제출됨, 심사 대기. */
	PENDING("심사 대기"),

	/** 심사 승인(직장 확인됨). */
	APPROVED("승인"),

	/** 심사 반려. */
	REJECTED("반려"),
}
