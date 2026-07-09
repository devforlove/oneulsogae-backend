package com.org.meeple.core.user.command.application.port.out

/**
 * KCP 암호화 아웃포트. 거래등록 enc_data 생성(encrypJson)과 결과 복호화(decryptJson)를 격리한다.
 * 현재는 stub(passthrough). KCP 공식 라이브러리 확보 시 구현체만 교체한다.
 */
interface KcpCertCryptoPort {
	fun encryptRegisterData(plainJson: String): String

	fun decryptCertData(encCertData: String): String
}
