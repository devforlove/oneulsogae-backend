package com.org.meeple.api.user.request

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

/** 학교 이메일 인증번호 발송 요청. 인증번호를 받을 학교 이메일을 제출한다. */
data class RequestUniversityEmailRequest(
	@field:NotBlank(message = "학교 이메일은 필수입니다.")
	@field:Email(message = "올바른 이메일 형식이 아닙니다.")
	val universityEmail: String,
)
