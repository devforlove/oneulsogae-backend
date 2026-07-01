package com.org.meeple.api.user.request

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size

/**
 * 보조 이메일(마케팅·광고·매칭 알림 수신용) 설정 요청 본문.
 * null 또는 공백을 넘기면 보조 이메일을 해제한다. 값이 있을 때만 이메일 형식을 검사한다.
 */
data class UpdateSecondaryEmailRequest(
	@field:Email(message = "보조 이메일 형식이 올바르지 않습니다.")
	@field:Size(max = 255, message = "보조 이메일은 255자 이하여야 합니다.")
	val secondaryEmail: String? = null,
)
