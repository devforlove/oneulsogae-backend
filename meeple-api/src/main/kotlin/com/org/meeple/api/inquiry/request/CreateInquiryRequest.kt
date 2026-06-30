package com.org.meeple.api.inquiry.request

import com.org.meeple.common.inquiry.InquiryCategory
import com.org.meeple.core.inquiry.command.application.port.`in`.command.CreateInquiryCommand
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class CreateInquiryRequest(
	@field:NotNull(message = "문의 유형은 필수입니다.")
	val category: InquiryCategory? = null,

	@field:NotBlank(message = "이메일은 필수입니다.")
	@field:Email(message = "유효한 이메일 형식이 아닙니다.")
	val email: String? = null,

	@field:NotBlank(message = "문의 내용은 필수입니다.")
	@field:Size(min = 10, max = 1000, message = "문의 내용은 10자 이상 1000자 이하여야 합니다.")
	val message: String? = null,
) {
	fun toCommand(userId: Long?): CreateInquiryCommand =
		CreateInquiryCommand(
			userId = userId,
			category = category!!,
			email = email!!,
			message = message!!,
		)
}
