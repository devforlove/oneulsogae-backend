package com.org.meeple.api.admin.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** 회사 이미지 인증 승인 요청. 어드민이 기입한 회사명을 유저 프로필에 확정한다. */
data class AdminApproveCompanyVerificationRequest(
	// company_name 컬럼(length=100) 초과로 인한 저장 오류를 막고, 회사명 직접 입력(ResolveCompanyNameRequest)과 상한을 맞춘다.
	@field:NotBlank(message = "회사명은 필수입니다.")
	@field:Size(max = 50, message = "회사명은 50자 이하여야 합니다.")
	val companyName: String? = null,
)
