package com.org.meeple.infra.user.command.repository

import com.org.meeple.core.user.command.domain.IdentityVerificationStatus
import com.org.meeple.infra.user.command.entity.IdentityVerificationEntity
import org.springframework.data.jpa.repository.JpaRepository

interface IdentityVerificationJpaRepository : JpaRepository<IdentityVerificationEntity, Long> {

	fun findFirstByUserIdOrderByIdDesc(userId: Long): IdentityVerificationEntity?

	fun existsByDiAndStatusAndUserIdNot(
		di: String,
		status: IdentityVerificationStatus,
		userId: Long,
	): Boolean
}
