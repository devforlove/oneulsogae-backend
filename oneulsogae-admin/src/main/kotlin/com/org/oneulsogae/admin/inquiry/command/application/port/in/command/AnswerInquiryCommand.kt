package com.org.oneulsogae.admin.inquiry.command.application.port.`in`.command

/** 어드민 문의 답변 커맨드. */
data class AnswerInquiryCommand(
	val inquiryId: Long,
	val answer: String,
)
