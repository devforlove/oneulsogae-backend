package com.org.oneulsogae.infra.user.command.adapter

import com.org.oneulsogae.core.user.command.application.port.out.AnonymizeIdentityVerificationPort
import com.org.oneulsogae.core.user.command.application.port.out.ExistsIdentityByPhoneNumberPort
import com.org.oneulsogae.core.user.command.application.port.out.GetIdentityVerificationPort
import com.org.oneulsogae.core.user.command.application.port.out.SaveIdentityVerificationPort
import com.org.oneulsogae.core.user.command.domain.IdentityVerification
import com.org.oneulsogae.core.user.command.domain.IdentityVerificationStatus
import com.org.oneulsogae.infra.user.command.crypto.CiCipher
import com.org.oneulsogae.infra.user.command.entity.IdentityVerificationEntity
import com.org.oneulsogae.infra.user.command.mapper.toDomain
import com.org.oneulsogae.infra.user.command.mapper.toEntity
import com.org.oneulsogae.infra.user.command.repository.IdentityVerificationJpaRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class IdentityVerificationRepositoryAdapter(
	private val identityVerificationJpaRepository: IdentityVerificationJpaRepository,
	private val ciCipher: CiCipher,
) : SaveIdentityVerificationPort, GetIdentityVerificationPort, ExistsIdentityByPhoneNumberPort, AnonymizeIdentityVerificationPort {

	override fun save(verification: IdentityVerification): IdentityVerification =
		identityVerificationJpaRepository.save(verification.toEntity(ciCipher)).toDomain()

	override fun findLatestByUserId(userId: Long): IdentityVerification? =
		identityVerificationJpaRepository.findFirstByUserIdOrderByIdDesc(userId)?.toDomain()

	override fun existsVerifiedByPhoneNumberOnOtherUser(phoneNumber: String, userId: Long): Boolean =
		identityVerificationJpaRepository.existsByPhoneNumberAndStatusAndUserIdNot(
			phoneNumber, IdentityVerificationStatus.VERIFIED, userId,
		)

	override fun anonymize(userId: Long, at: LocalDateTime) {
		val entities: List<IdentityVerificationEntity> = identityVerificationJpaRepository.findAllByUserId(userId)
		entities.forEach { entity: IdentityVerificationEntity ->
			entity.anonymize()
			entity.softDelete(at)
		}
		identityVerificationJpaRepository.saveAll(entities)
	}
}
