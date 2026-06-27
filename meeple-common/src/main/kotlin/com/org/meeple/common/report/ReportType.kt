package com.org.meeple.common.report

/** 사용자 신고 사유. */
enum class ReportType(val description: String) {
	SPAM_ADVERTISEMENT("스팸·광고"),
	ABUSE_DEFAMATION("욕설·비방"),
	OBSCENE_INAPPROPRIATE("음란성·부적절한 콘텐츠"),
	FRAUD_IMPERSONATION("사기·사칭"),
	ETC("기타"),
}
