package com.org.meeple.core.inquiry.command.application.port.out

import com.org.meeple.core.inquiry.command.domain.Inquiry

interface SaveInquiryPort {

	fun save(inquiry: Inquiry): Inquiry
}
