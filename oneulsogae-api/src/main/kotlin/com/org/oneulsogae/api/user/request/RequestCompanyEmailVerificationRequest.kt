package com.org.oneulsogae.api.user.request

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** 회사 이메일 인증번호 발송 요청. 입력한 회사 이메일로 1회용 인증번호를 발송한다. */
data class RequestCompanyEmailVerificationRequest(
	@field:NotBlank(message = "회사 이메일은 필수입니다.")
	@field:Email(message = "회사 이메일 형식이 올바르지 않습니다.")
	@field:Size(max = 255, message = "회사 이메일은 255자 이하여야 합니다.")
	val companyEmail: String,
)
