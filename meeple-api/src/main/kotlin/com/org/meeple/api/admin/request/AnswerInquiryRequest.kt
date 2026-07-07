package com.org.meeple.api.admin.request

import com.org.meeple.admin.inquiry.command.application.port.`in`.command.AnswerInquiryCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AnswerInquiryRequest(
	@field:NotBlank(message = "답변 내용은 필수입니다.")
	@field:Size(max = 2000, message = "답변 내용은 2000자 이하여야 합니다.")
	val answer: String? = null,
) {
	// @Valid 통과 후 호출 → answer non-null/non-blank 보장 → command로 변환
	fun toCommand(inquiryId: Long): AnswerInquiryCommand =
		AnswerInquiryCommand(
			inquiryId = inquiryId,
			answer = answer!!,
		)
}
