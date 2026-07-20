package com.org.oneulsogae.core.inquiry.command.application.port.`in`.command

import com.org.oneulsogae.common.inquiry.InquiryCategory

data class CreateInquiryCommand(
	val userId: Long?,
	val category: InquiryCategory,
	val email: String,
	val message: String,
)
