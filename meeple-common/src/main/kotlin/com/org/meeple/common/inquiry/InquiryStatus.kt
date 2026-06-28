package com.org.meeple.common.inquiry

/** 문의 처리 상태. */
enum class InquiryStatus(val description: String) {

	/** 접수됨. 운영자 답변 대기 중. */
	PENDING("대기"),

	/** 운영자 답변 완료. */
	ANSWERED("답변완료"),
}
