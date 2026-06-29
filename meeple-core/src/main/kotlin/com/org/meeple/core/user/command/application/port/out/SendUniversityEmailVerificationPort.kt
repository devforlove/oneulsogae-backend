package com.org.meeple.core.user.command.application.port.out

/**
 * 학교 이메일 인증번호 발송 아웃포트.
 * 1회용 인증번호(code)를 대상 학교 이메일로 발송한다.
 * 실제 발송 수단(메일 본문 구성 등)은 infra 레이어의 어댑터가 책임진다.
 */
interface SendUniversityEmailVerificationPort {

	fun send(toEmail: String, code: String)
}
