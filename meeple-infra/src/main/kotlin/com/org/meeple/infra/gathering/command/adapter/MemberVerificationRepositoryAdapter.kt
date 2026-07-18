package com.org.meeple.infra.gathering.command.adapter

import com.org.meeple.admin.memberverification.command.application.port.out.GetMemberVerificationPort
import com.org.meeple.admin.memberverification.command.application.port.out.SaveMemberVerificationPort as SaveAdminMemberVerificationPort
import com.org.meeple.admin.memberverification.command.domain.AdminMemberVerification
import com.org.meeple.core.gathering.command.application.port.out.SaveMemberVerificationPort
import com.org.meeple.core.gathering.command.domain.MemberVerification
import com.org.meeple.infra.gathering.command.entity.MemberVerificationEntity
import com.org.meeple.infra.gathering.command.mapper.toDomain
import com.org.meeple.infra.gathering.command.mapper.toEntity
import com.org.meeple.infra.gathering.command.repository.MemberVerificationJpaRepository
import org.springframework.stereotype.Component

/**
 * 멤버 인증(본인인증) 엔티티의 out-port 어댑터. (엔티티당 어댑터 하나)
 * core [SaveMemberVerificationPort](제출 저장)와 admin 심사 포트([GetMemberVerificationPort]·
 * [SaveAdminMemberVerificationPort])를 함께 구현한다. (동명 Save 포트는 import alias로 구분)
 * 조회는 query의 [com.org.meeple.infra.gathering.query.GetMemberVerificationDaoImpl]·
 * [com.org.meeple.infra.gathering.query.GetAdminMemberVerificationDaoImpl]이 따로 구현한다.
 */
@Component
class MemberVerificationRepositoryAdapter(
	private val memberVerificationJpaRepository: MemberVerificationJpaRepository,
) : SaveMemberVerificationPort, GetMemberVerificationPort, SaveAdminMemberVerificationPort {

	// id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge). 둘 다 Spring Data save가 처리한다.
	override fun save(verification: MemberVerification): MemberVerification =
		memberVerificationJpaRepository.save(verification.toEntity()).toDomain()

	// admin 심사: id로 인증을 조회한다. (@SQLRestriction으로 soft-delete 행 제외)
	override fun findById(id: Long): AdminMemberVerification? =
		memberVerificationJpaRepository.findById(id)
			.map { entity: MemberVerificationEntity -> entity.toAdminDomain() }
			.orElse(null)

	// admin 심사: 기존 행을 로드해 status·rejectionReason을 반영해 저장한다. (이미지 키/직업 정보 등 다른 필드는 보존)
	override fun save(verification: AdminMemberVerification): AdminMemberVerification {
		val entity: MemberVerificationEntity = memberVerificationJpaRepository.findById(verification.id)
			.orElseThrow { IllegalStateException("멤버 인증을 찾을 수 없습니다: ${verification.id}") }
		entity.status = verification.status
		entity.rejectionReason = verification.rejectionReason
		return memberVerificationJpaRepository.save(entity).toAdminDomain()
	}

	private fun MemberVerificationEntity.toAdminDomain(): AdminMemberVerification =
		AdminMemberVerification(
			id = id ?: 0,
			userId = userId,
			status = status,
			rejectionReason = rejectionReason,
		)
}
