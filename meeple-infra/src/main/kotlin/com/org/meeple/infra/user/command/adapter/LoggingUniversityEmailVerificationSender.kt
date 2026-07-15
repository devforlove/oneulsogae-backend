package com.org.meeple.infra.user.command.adapter

import com.org.meeple.core.user.command.application.port.out.SendUniversityEmailVerificationPort
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * [SendUniversityEmailVerificationPort]의 스텁 구현.
 * 실제 메일을 보내지 않고 인증번호를 로그로 남긴다. (개발/연동 전 단계용)
 * prod는 SES 어댑터([SesUniversityEmailVerificationSender])가 발송을 담당한다.
 */
@Component
@Profile("!prod")
class LoggingUniversityEmailVerificationSender : SendUniversityEmailVerificationPort {

	private val log = LoggerFactory.getLogger(javaClass)

	override fun send(toEmail: String, code: String) {
		log.info("[학교 이메일 인증번호 발송 - 스텁] to={}, code={}", toEmail, code)
	}
}
