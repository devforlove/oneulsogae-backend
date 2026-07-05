package com.org.meeple.admin.inquiry.command.application.port.`in`

import com.org.meeple.admin.inquiry.command.application.port.`in`.command.AnswerInquiryCommand

/** 어드민 문의 답변 유스케이스. (명령) */
interface AnswerInquiryUseCase {

	/** 문의에 답변한다. 없으면 INQUIRY_NOT_FOUND, 이미 답변됐으면 INQUIRY_ALREADY_ANSWERED. */
	fun answer(command: AnswerInquiryCommand)
}
