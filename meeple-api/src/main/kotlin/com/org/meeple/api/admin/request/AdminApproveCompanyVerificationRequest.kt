package com.org.meeple.api.admin.request

import jakarta.validation.constraints.NotBlank

/** 회사 이미지 인증 승인 요청. 어드민이 기입한 회사명을 유저 프로필에 확정한다. */
data class AdminApproveCompanyVerificationRequest(
	@field:NotBlank(message = "회사명은 필수입니다.")
	val companyName: String? = null,
)
