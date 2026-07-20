package com.org.oneulsogae.core.inquiry.command.application.port.`in`

import com.org.oneulsogae.core.inquiry.command.application.port.`in`.command.CreateInquiryCommand
import com.org.oneulsogae.core.inquiry.command.domain.Inquiry

interface CreateInquiryUseCase {

	fun create(command: CreateInquiryCommand): Inquiry
}
