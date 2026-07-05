package com.org.meeple.admin.inquiry.command.application.port.out

import com.org.meeple.admin.inquiry.command.domain.AdminInquiry

/** 답변 대상 문의 로드 out-port. 없거나 soft-delete면 null. */
fun interface GetAdminInquiryPort {
	fun findById(id: Long): AdminInquiry?
}
