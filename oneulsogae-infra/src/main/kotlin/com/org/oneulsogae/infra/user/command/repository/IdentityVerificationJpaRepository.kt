package com.org.oneulsogae.infra.user.command.repository

import com.org.oneulsogae.core.user.command.domain.IdentityVerificationStatus
import com.org.oneulsogae.infra.user.command.entity.IdentityVerificationEntity
import org.springframework.data.jpa.repository.JpaRepository

interface IdentityVerificationJpaRepository : JpaRepository<IdentityVerificationEntity, Long> {

	fun findFirstByUserIdOrderByIdDesc(userId: Long): IdentityVerificationEntity?

	fun findAllByUserId(userId: Long): List<IdentityVerificationEntity>

	fun existsByPhoneNumberAndStatusAndUserIdNot(
		phoneNumber: String,
		status: IdentityVerificationStatus,
		userId: Long,
	): Boolean
}
