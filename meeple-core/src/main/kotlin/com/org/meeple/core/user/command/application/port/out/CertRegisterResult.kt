package com.org.meeple.core.user.command.application.port.out

/** KCP 거래등록 결과. 프론트가 인증창 호출에 사용한다. */
data class CertRegisterResult(
	val regCertKey: String,
	val callUrl: String,
)
