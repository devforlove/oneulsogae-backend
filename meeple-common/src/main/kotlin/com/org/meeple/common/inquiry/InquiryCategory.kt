package com.org.meeple.common.inquiry

/** 고객센터 문의 유형. 프론트 INQUIRY_CATEGORIES와 1:1로 대응한다. */
enum class InquiryCategory(val description: String) {

	ACCOUNT("계정·로그인"),
	PAYMENT("결제·코인"),
	MATCHING("매칭·채팅"),
	REPORT("신고·이용 제한"),
	ETC("기타 문의"),
}
