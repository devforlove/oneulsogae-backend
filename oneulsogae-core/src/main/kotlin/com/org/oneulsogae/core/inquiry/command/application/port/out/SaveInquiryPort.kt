package com.org.oneulsogae.core.inquiry.command.application.port.out

import com.org.oneulsogae.core.inquiry.command.domain.Inquiry

interface SaveInquiryPort {

	fun save(inquiry: Inquiry): Inquiry
}
