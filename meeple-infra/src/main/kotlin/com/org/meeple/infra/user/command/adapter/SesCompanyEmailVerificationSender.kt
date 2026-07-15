package com.org.meeple.infra.user.command.adapter

import com.org.meeple.core.user.command.application.port.out.SendCompanyEmailVerificationPort
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/** [SendCompanyEmailVerificationPort]의 SES 구현. prod 프로파일에서만 활성화된다. */
@Component
@Profile("prod")
class SesCompanyEmailVerificationSender(
	private val mailSender: SesVerificationMailSender,
) : SendCompanyEmailVerificationPort {

	override fun send(toEmail: String, code: String) {
		mailSender.send(
			toEmail = toEmail,
			subject = "[미플] 회사 이메일 인증번호",
			body = """
				|인증번호: $code
				|
				|미플에서 요청하신 회사 이메일 인증번호입니다.
				|10분 안에 화면에 입력해 주세요.
				|
				|본인이 요청하지 않았다면 이 메일을 무시하셔도 됩니다.
			""".trimMargin(),
		)
	}
}
