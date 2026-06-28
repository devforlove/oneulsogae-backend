package com.org.meeple.api.inquiry.response

import com.org.meeple.core.inquiry.command.domain.Inquiry

data class CreateInquiryResponse(
	val inquiryId: Long,
) {
	companion object {
		fun of(inquiry: Inquiry): CreateInquiryResponse =
			CreateInquiryResponse(inquiryId = inquiry.id)
	}
}
