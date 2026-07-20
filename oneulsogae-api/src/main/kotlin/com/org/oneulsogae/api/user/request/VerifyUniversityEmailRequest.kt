package com.org.oneulsogae.api.user.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

/** 학교 이메일 인증번호 검증 요청. 이메일로 받은 6자리 인증번호를 제출한다. */
data class VerifyUniversityEmailRequest(
	@field:NotBlank(message = "인증번호는 필수입니다.")
	@field:Pattern(regexp = "\\d{6}", message = "인증번호는 6자리 숫자여야 합니다.")
	val code: String,
)
