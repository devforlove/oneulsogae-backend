package com.org.meeple.infra.user.command.adapter

import com.org.meeple.admin.companyverification.command.application.port.out.GetCompanyImageVerificationPort
import com.org.meeple.admin.companyverification.command.application.port.out.SaveCompanyImageVerificationPort as SaveAdminCompanyImageVerificationPort
import com.org.meeple.admin.companyverification.command.domain.AdminCompanyImageVerification
import com.org.meeple.core.user.command.application.port.out.SaveCompanyImageVerificationPort
import com.org.meeple.core.user.command.domain.CompanyImageVerification
import com.org.meeple.infra.user.command.entity.CompanyImageVerificationEntity
import com.org.meeple.infra.user.command.mapper.toDomain
import com.org.meeple.infra.user.command.mapper.toEntity
import com.org.meeple.infra.user.command.repository.CompanyImageVerificationJpaRepository
import org.springframework.stereotype.Component

/**
 * 직장 서류 이미지 인증 엔티티의 out-port 어댑터. (엔티티당 어댑터 하나)
 * core [SaveCompanyImageVerificationPort](제출 저장)와 admin 심사 포트([GetCompanyImageVerificationPort]·
 * [SaveAdminCompanyImageVerificationPort])를 함께 구현한다. (동명 Save 포트는 import alias로 구분)
 */
@Component
class CompanyImageVerificationRepositoryAdapter(
	private val companyImageVerificationJpaRepository: CompanyImageVerificationJpaRepository,
) : SaveCompanyImageVerificationPort, GetCompanyImageVerificationPort, SaveAdminCompanyImageVerificationPort {

	// id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge). 둘 다 Spring Data save가 처리한다.
	override fun save(verification: CompanyImageVerification): CompanyImageVerification =
		companyImageVerificationJpaRepository.save(verification.toEntity()).toDomain()

	// admin 심사: id로 인증을 조회한다. (@SQLRestriction으로 soft-delete 행 제외)
	override fun findById(id: Long): AdminCompanyImageVerification? =
		companyImageVerificationJpaRepository.findById(id)
			.map { entity: CompanyImageVerificationEntity -> entity.toAdminDomain() }
			.orElse(null)

	// admin 심사: 기존 행을 로드해 status·rejectionReason을 반영해 저장한다. (imageKey/userId/companyName 보존)
	override fun save(verification: AdminCompanyImageVerification): AdminCompanyImageVerification {
		val entity: CompanyImageVerificationEntity = companyImageVerificationJpaRepository.findById(verification.id)
			.orElseThrow { IllegalStateException("직장 인증을 찾을 수 없습니다: ${verification.id}") }
		entity.status = verification.status
		entity.rejectionReason = verification.rejectionReason
		return companyImageVerificationJpaRepository.save(entity).toAdminDomain()
	}

	private fun CompanyImageVerificationEntity.toAdminDomain(): AdminCompanyImageVerification =
		AdminCompanyImageVerification(
			id = id ?: 0,
			userId = userId,
			status = status,
			rejectionReason = rejectionReason,
		)
}
