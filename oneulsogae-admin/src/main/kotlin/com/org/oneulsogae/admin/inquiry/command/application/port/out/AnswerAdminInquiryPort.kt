package com.org.oneulsogae.admin.inquiry.command.application.port.out

import com.org.oneulsogae.admin.inquiry.command.domain.AnsweredInquiry

/** 문의 답변 저장 out-port. answer/answered_at 저장 + status=ANSWERED 전이. infra 어댑터가 구현한다. */
fun interface AnswerAdminInquiryPort {
	fun answer(answered: AnsweredInquiry)
}
