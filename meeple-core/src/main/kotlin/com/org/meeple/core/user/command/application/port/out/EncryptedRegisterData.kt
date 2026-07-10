package com.org.meeple.core.user.command.application.port.out

/**
 * KCP 거래등록 요청 암호화 결과. enc_data는 요청 본문으로, rv는 요청 헤더로 전송된다.
 */
data class EncryptedRegisterData(
	val encData: String,
	val rv: String,
)
