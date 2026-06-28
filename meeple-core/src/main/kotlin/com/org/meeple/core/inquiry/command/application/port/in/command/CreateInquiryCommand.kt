package com.org.meeple.core.inquiry.command.application.port.`in`.command

import com.org.meeple.common.inquiry.InquiryCategory

data class CreateInquiryCommand(
	val userId: Long,
	val category: InquiryCategory,
	val email: String,
	val message: String,
)
