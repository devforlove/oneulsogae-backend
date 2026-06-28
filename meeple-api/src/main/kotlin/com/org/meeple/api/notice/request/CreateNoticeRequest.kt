package com.org.meeple.api.notice.request

import com.org.meeple.core.notice.command.application.port.`in`.command.CreateNoticeCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateNoticeRequest(
	@field:NotBlank(message = "공지 제목은 필수입니다.")
	@field:Size(max = 200, message = "공지 제목은 200자 이하여야 합니다.")
	val title: String? = null,

	@field:NotBlank(message = "공지 설명은 필수입니다.")
	@field:Size(max = 2000, message = "공지 설명은 2000자 이하여야 합니다.")
	val description: String? = null,
) {
	// @Valid 통과 후 호출 → 필수 필드(title·description) non-null/non-blank 보장 → command로 변환
	fun toCommand(): CreateNoticeCommand =
		CreateNoticeCommand(
			title = title!!,
			description = description!!,
		)
}
