package com.org.meeple.infra.user.command.adapter

import com.org.meeple.core.user.command.application.port.out.SendCompanyEmailVerificationPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * [SendCompanyEmailVerificationPort]의 스텁 구현.
 * 실제 메일을 보내지 않고 인증번호를 로그로 남긴다. (개발/연동 전 단계용)
 * 추후 SMTP/외부 메일 API 어댑터로 교체하면 된다.
 */
@Component
class LoggingCompanyEmailVerificationSender : SendCompanyEmailVerificationPort {

	private val log = LoggerFactory.getLogger(javaClass)

	override fun send(toEmail: String, code: String) {
		log.info("[회사 이메일 인증번호 발송 - 스텁] to={}, code={}", toEmail, code)
	}
}
