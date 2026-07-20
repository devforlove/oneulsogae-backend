package com.org.oneulsogae.api.admin.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * 멤버 인증 승인 요청. 어드민이 심사에서 확정한 회사명·직종·직장 상세를 받아 유저 프로필에 반영한다.
 */
data class AdminApproveMemberVerificationRequest(
	@field:NotBlank(message = "회사명은 필수입니다.")
	@field:Size(max = 100, message = "회사명은 100자 이하여야 합니다.")
	val companyName: String? = null,

	@field:NotBlank(message = "직종은 필수입니다.")
	@field:Size(max = 30, message = "직종은 30자 이하여야 합니다.")
	val jobCategory: String? = null,

	@field:NotBlank(message = "직장 상세는 필수입니다.")
	@field:Size(max = 100, message = "직장 상세는 100자 이하여야 합니다.")
	val jobDetail: String? = null,
)
