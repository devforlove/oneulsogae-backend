package com.org.oneulsogae.infra.user.command.adapter

import com.org.oneulsogae.core.user.command.application.port.out.SendCompanyEmailVerificationPort
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
			subject = VerificationMailTemplate.subject(KIND),
			textBody = VerificationMailTemplate.text(KIND, code),
			htmlBody = VerificationMailTemplate.html(KIND, code),
		)
	}

	companion object {
		private const val KIND: String = "회사"
	}
}
