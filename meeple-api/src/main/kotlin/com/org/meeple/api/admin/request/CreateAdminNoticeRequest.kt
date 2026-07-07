package com.org.meeple.api.admin.request

import com.org.meeple.admin.notice.command.application.port.`in`.command.CreateAdminNoticeCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateAdminNoticeRequest(
	@field:NotBlank(message = "공지 제목은 필수입니다.")
	@field:Size(max = 200, message = "공지 제목은 200자 이하여야 합니다.")
	val title: String? = null,

	@field:NotBlank(message = "공지 설명은 필수입니다.")
	@field:Size(max = 2000, message = "공지 설명은 2000자 이하여야 합니다.")
	val description: String? = null,
) {
	// @Valid 통과 후 호출 → 필수 필드 non-null/non-blank 보장 → command로 변환
	fun toCommand(): CreateAdminNoticeCommand =
		CreateAdminNoticeCommand(
			title = title!!,
			description = description!!,
		)
}
