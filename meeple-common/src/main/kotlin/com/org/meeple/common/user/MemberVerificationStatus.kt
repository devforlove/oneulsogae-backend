package com.org.meeple.common.user

/**
 * 멤버 인증(본인인증)의 심사 상태.
 * 직업 정보·사진(얼굴/전신/서류)은 자동 검증이 불가능해 사람(어드민)이 확인해야 하므로, 제출 시 [PENDING]으로 시작한다.
 */
enum class MemberVerificationStatus(val description: String) {

	/** 제출됨, 심사 대기. */
	PENDING("심사 대기"),

	/** 심사 승인(멤버 인증 완료). */
	APPROVED("승인"),

	/** 심사 반려. */
	REJECTED("반려"),
}
