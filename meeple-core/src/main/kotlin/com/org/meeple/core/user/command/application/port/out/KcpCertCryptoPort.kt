package com.org.meeple.core.user.command.application.port.out

/**
 * KCP 암호화 아웃포트. 거래등록 요청 전문 암호화(encryptJson)와 결과 복호화(decryptJson)를 격리한다.
 * 구현체(infra)가 ENC_KEY·site_cd를 설정에서 채워 KCP 공식 라이브러리에 위임한다.
 */
interface KcpCertCryptoPort {
	/** 거래등록 요청 JSON을 암호화해 enc_data와 rv를 함께 반환한다. (KCP encryptJson) */
	fun encryptRegisterData(plainJson: String): EncryptedRegisterData

	/** 결과조회 응답의 enc_cert_data를 응답에 동봉된 rv로 복호화해 평문 JSON을 반환한다. (KCP decryptJson) */
	fun decryptCertData(encCertData: String, rv: String): String
}
