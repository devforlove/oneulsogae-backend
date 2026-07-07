package com.org.meeple.infra.inquiry.command.adapter

import com.org.meeple.admin.inquiry.command.application.port.out.AnswerAdminInquiryPort
import com.org.meeple.admin.inquiry.command.application.port.out.GetAdminInquiryPort
import com.org.meeple.admin.inquiry.command.domain.AdminInquiry
import com.org.meeple.admin.inquiry.command.domain.AnsweredInquiry
import com.org.meeple.common.inquiry.InquiryStatus
import com.org.meeple.core.inquiry.command.application.port.out.SaveInquiryPort
import com.org.meeple.core.inquiry.command.domain.Inquiry
import com.org.meeple.infra.inquiry.command.entity.InquiryEntity
import com.org.meeple.infra.inquiry.command.mapper.toDomain
import com.org.meeple.infra.inquiry.command.mapper.toEntity
import com.org.meeple.infra.inquiry.command.repository.InquiryJpaRepository
import org.springframework.stereotype.Component

/**
 * [InquiryEntity]의 command 영속성 어댑터. (엔티티당 어댑터 하나)
 * 유저용 저장 out-port([SaveInquiryPort])와 어드민 로드·답변 out-port([GetAdminInquiryPort]·[AnswerAdminInquiryPort])를 함께 구현한다.
 * 어드민 조회는 [com.org.meeple.infra.inquiry.query.GetAdminInquiryDaoImpl]가 따로 담당한다.
 */
@Component
class InquiryAdapter(
	private val inquiryJpaRepository: InquiryJpaRepository,
) : SaveInquiryPort, GetAdminInquiryPort, AnswerAdminInquiryPort {

	override fun save(inquiry: Inquiry): Inquiry =
		inquiryJpaRepository.save(inquiry.toEntity()).toDomain()

	override fun findById(id: Long): AdminInquiry? =
		inquiryJpaRepository.findById(id)
			.map { entity: InquiryEntity -> AdminInquiry(id = entity.id ?: 0, status = entity.status) }
			.orElse(null)

	// 기존 행을 로드해 answer/answered_at을 채우고 status를 ANSWERED로 전이해 저장한다. (다른 필드 보존)
	override fun answer(answered: AnsweredInquiry) {
		val entity: InquiryEntity = inquiryJpaRepository.findById(answered.id)
			.orElseThrow { IllegalStateException("문의를 찾을 수 없습니다: ${answered.id}") }
		entity.answer = answered.answer
		entity.answeredAt = answered.answeredAt
		entity.status = InquiryStatus.ANSWERED
		inquiryJpaRepository.save(entity)
	}
}
