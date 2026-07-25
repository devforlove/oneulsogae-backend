package com.org.oneulsogae.api.admin.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** 학교 이미지 인증 승인 요청. 어드민이 기입한 학교명을 유저 프로필에 확정한다. */
data class AdminApproveUniversityVerificationRequest(
	// university_name 컬럼(length=100) 초과로 인한 저장 오류를 막는다.
	@field:NotBlank(message = "학교명은 필수입니다.")
	@field:Size(max = 50, message = "학교명은 50자 이하여야 합니다.")
	val universityName: String? = null,
)
