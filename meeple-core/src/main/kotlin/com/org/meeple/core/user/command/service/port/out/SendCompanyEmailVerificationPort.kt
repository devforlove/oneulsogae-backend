package com.org.meeple.core.user.command.service.port.out

/**
 * 회사 이메일 인증번호 발송 아웃포트.
 * 1회용 인증번호(code)를 대상 회사 이메일로 발송한다.
 * 실제 발송 수단(메일 본문 구성 등)은 infra 레이어의 어댑터가 책임진다.
 */
interface SendCompanyEmailVerificationPort {

	fun send(toEmail: String, code: String)
}
