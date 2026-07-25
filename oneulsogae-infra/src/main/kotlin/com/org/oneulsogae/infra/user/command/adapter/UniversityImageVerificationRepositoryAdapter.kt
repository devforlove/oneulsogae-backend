package com.org.oneulsogae.infra.user.command.adapter

import com.org.oneulsogae.admin.universityverification.command.application.port.out.GetUniversityImageVerificationPort
import com.org.oneulsogae.admin.universityverification.command.application.port.out.SaveUniversityImageVerificationPort as SaveAdminUniversityImageVerificationPort
import com.org.oneulsogae.admin.universityverification.command.domain.AdminUniversityImageVerification
import com.org.oneulsogae.core.user.command.application.port.out.SaveUniversityImageVerificationPort
import com.org.oneulsogae.core.user.command.domain.UniversityImageVerification
import com.org.oneulsogae.infra.user.command.entity.UniversityImageVerificationEntity
import com.org.oneulsogae.infra.user.command.mapper.toDomain
import com.org.oneulsogae.infra.user.command.mapper.toEntity
import com.org.oneulsogae.infra.user.command.repository.UniversityImageVerificationJpaRepository
import org.springframework.stereotype.Component

/**
 * 학교 서류 이미지 인증 엔티티의 out-port 어댑터. (엔티티당 어댑터 하나)
 * core [SaveUniversityImageVerificationPort](제출 저장)와 admin 심사 포트([GetUniversityImageVerificationPort]·
 * [SaveAdminUniversityImageVerificationPort])를 함께 구현한다. (동명 Save 포트는 import alias로 구분)
 */
@Component
class UniversityImageVerificationRepositoryAdapter(
	private val universityImageVerificationJpaRepository: UniversityImageVerificationJpaRepository,
) : SaveUniversityImageVerificationPort, GetUniversityImageVerificationPort, SaveAdminUniversityImageVerificationPort {

	// id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge). 둘 다 Spring Data save가 처리한다.
	override fun save(verification: UniversityImageVerification): UniversityImageVerification =
		universityImageVerificationJpaRepository.save(verification.toEntity()).toDomain()

	// admin 심사: id로 인증을 조회한다. (@SQLRestriction으로 soft-delete 행 제외)
	override fun findById(id: Long): AdminUniversityImageVerification? =
		universityImageVerificationJpaRepository.findById(id)
			.map { entity: UniversityImageVerificationEntity -> entity.toAdminDomain() }
			.orElse(null)

	// admin 심사: 기존 행을 로드해 status·rejectionReason을 반영해 저장한다. (imageKey/userId/universityName 보존)
	override fun save(verification: AdminUniversityImageVerification): AdminUniversityImageVerification {
		val entity: UniversityImageVerificationEntity = universityImageVerificationJpaRepository.findById(verification.id)
			.orElseThrow { IllegalStateException("학교 인증을 찾을 수 없습니다: ${verification.id}") }
		entity.status = verification.status
		entity.rejectionReason = verification.rejectionReason
		return universityImageVerificationJpaRepository.save(entity).toAdminDomain()
	}

	private fun UniversityImageVerificationEntity.toAdminDomain(): AdminUniversityImageVerification =
		AdminUniversityImageVerification(
			id = id ?: 0,
			userId = userId,
			status = status,
			rejectionReason = rejectionReason,
		)
}
