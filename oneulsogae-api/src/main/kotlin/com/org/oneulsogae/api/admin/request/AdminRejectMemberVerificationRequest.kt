package com.org.oneulsogae.api.admin.request

import jakarta.validation.constraints.Size

/** 멤버 인증 반려 요청. 반려 사유(선택)를 저장한다. */
data class AdminRejectMemberVerificationRequest(
	@field:Size(max = 500, message = "반려 사유는 500자 이하여야 합니다.")
	val reason: String? = null,
)
