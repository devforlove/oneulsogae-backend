package com.org.meeple.api.user.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** 회사명 직접 입력 요청. 회사명을 제출하면 정식 가입(ACTIVE) 처리된다. */
data class ResolveCompanyNameRequest(
	@field:NotBlank(message = "회사명은 필수입니다.")
	@field:Size(max = 50, message = "회사명은 50자 이하여야 합니다.")
	val companyName: String,
)
