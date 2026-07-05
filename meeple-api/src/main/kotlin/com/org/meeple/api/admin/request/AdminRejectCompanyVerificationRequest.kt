package com.org.meeple.api.admin.request

import jakarta.validation.constraints.Size

/** 회사 이미지 인증 반려 요청. 반려 사유(선택)를 저장한다. */
data class AdminRejectCompanyVerificationRequest(
	@field:Size(max = 500, message = "반려 사유는 500자 이하여야 합니다.")
	val reason: String? = null,
)
