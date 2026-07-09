package com.org.meeple.infra.user.command.adapter

import com.org.meeple.core.user.command.application.port.out.KcpCertCryptoPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * KCP 암호화 stub. 공식 라이브러리(encrypJson/decryptJson) 확보 전까지 평문을 그대로 통과시킨다.
 * 실 KCP 서버는 평문을 거부하므로, 이 stub은 WireMock/페이크 대상 테스트에서만 유효하다.
 * TODO: KCP 공식 Java 라이브러리로 교체(ENC_KEY 사용).
 */
@Component
class KcpCertCryptoStubAdapter : KcpCertCryptoPort {

	private val log = LoggerFactory.getLogger(javaClass)

	override fun encryptRegisterData(plainJson: String): String {
		log.warn("[KCP 암호화 미구현 - stub] 평문 통과. 실 연동 전까지만 사용.")
		return plainJson
	}

	override fun decryptCertData(encCertData: String): String {
		log.warn("[KCP 복호화 미구현 - stub] 입력 통과. 실 연동 전까지만 사용.")
		return encCertData
	}
}
