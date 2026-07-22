package com.org.oneulsogae.infra.user.command.adapter

import com.org.oneulsogae.infra.config.SesProperties
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sesv2.SesV2Client
import software.amazon.awssdk.services.sesv2.model.Body
import software.amazon.awssdk.services.sesv2.model.Content
import software.amazon.awssdk.services.sesv2.model.Destination
import software.amazon.awssdk.services.sesv2.model.EmailContent
import software.amazon.awssdk.services.sesv2.model.Message
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest

/**
 * SES 텍스트 메일 발송 공용 컴포넌트. 회사/학교 인증번호 어댑터가 문구만 달리해 위임한다.
 * 발송 실패(SES 예외)는 그대로 전파한다 — 요청 트랜잭션이 롤백되어 인증 레코드가 남지 않고, 클라이언트가 재시도한다.
 */
@Component
@Profile("prod")
class SesVerificationMailSender(
	private val sesClient: SesV2Client,
	private val properties: SesProperties,
) {

	fun send(toEmail: String, subject: String, textBody: String, htmlBody: String) {
		val request: SendEmailRequest = SendEmailRequest.builder()
			.fromEmailAddress(properties.fromAddress)
			.destination(Destination.builder().toAddresses(toEmail).build())
			.content(
				EmailContent.builder()
					.simple(
						Message.builder()
							.subject(Content.builder().data(subject).charset(CHARSET).build())
							.body(
								Body.builder()
									.html(Content.builder().data(htmlBody).charset(CHARSET).build())
									.text(Content.builder().data(textBody).charset(CHARSET).build())
									.build(),
							)
							.build(),
					)
					.build(),
			)
			.build()
		sesClient.sendEmail(request)
	}

	companion object {
		private const val CHARSET: String = "UTF-8"
	}
}
