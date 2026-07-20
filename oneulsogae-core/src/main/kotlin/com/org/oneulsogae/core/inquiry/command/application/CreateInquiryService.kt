package com.org.oneulsogae.core.inquiry.command.application

import com.org.oneulsogae.core.inquiry.command.application.port.`in`.CreateInquiryUseCase
import com.org.oneulsogae.core.inquiry.command.application.port.`in`.command.CreateInquiryCommand
import com.org.oneulsogae.core.inquiry.command.application.port.out.SaveInquiryPort
import com.org.oneulsogae.core.inquiry.command.domain.Inquiry
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CreateInquiryService(
	private val saveInquiryPort: SaveInquiryPort,
) : CreateInquiryUseCase {

	override fun create(command: CreateInquiryCommand): Inquiry =
		saveInquiryPort.save(
			Inquiry.create(
				userId = command.userId,
				category = command.category,
				email = command.email,
				message = command.message,
			),
		)
}
