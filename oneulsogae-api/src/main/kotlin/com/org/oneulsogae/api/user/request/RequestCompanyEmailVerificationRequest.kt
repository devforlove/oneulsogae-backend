package com.org.oneulsogae.api.user.request

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * 회사 이메일 인증번호 발송 요청. 입력한 회사 이메일로 1회용 인증번호를 발송한다.
 * 같은 도메인을 쓰는 회사가 여럿이면 첫 요청 응답으로 후보 목록을 받고, [companyId]를 지정해 재요청한다.
 */
data class RequestCompanyEmailVerificationRequest(
	@field:NotBlank(message = "회사 이메일은 필수입니다.")
	@field:Email(message = "회사 이메일 형식이 올바르지 않습니다.")
	@field:Size(max = 255, message = "회사 이메일은 255자 이하여야 합니다.")
	val companyEmail: String,

	/** 선택한 회사 id. 도메인에 매핑된 회사가 하나면 생략 가능. */
	val companyId: Long? = null,
)
