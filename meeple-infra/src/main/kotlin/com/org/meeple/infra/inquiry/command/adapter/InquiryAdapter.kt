package com.org.meeple.infra.inquiry.command.adapter

import com.org.meeple.core.inquiry.command.application.port.out.SaveInquiryPort
import com.org.meeple.core.inquiry.command.domain.Inquiry
import com.org.meeple.infra.inquiry.command.mapper.toDomain
import com.org.meeple.infra.inquiry.command.mapper.toEntity
import com.org.meeple.infra.inquiry.command.repository.InquiryJpaRepository
import org.springframework.stereotype.Component

/**
 * [InquiryEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나)
 * 문의 저장 out-port([SaveInquiryPort])를 구현한다.
 */
@Component
class InquiryAdapter(
	private val inquiryJpaRepository: InquiryJpaRepository,
) : SaveInquiryPort {

	override fun save(inquiry: Inquiry): Inquiry =
		inquiryJpaRepository.save(inquiry.toEntity()).toDomain()
}
