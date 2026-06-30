package com.org.meeple.infra.user.command.adapter

import com.org.meeple.core.user.command.application.port.out.AnonymizeUserPort
import com.org.meeple.core.user.command.application.port.out.GetUserPort
import com.org.meeple.core.user.command.application.port.out.RestoreUserPort
import com.org.meeple.core.user.command.application.port.out.SaveUserPort
import com.org.meeple.core.user.command.application.port.out.SoftDeleteUserPort
import com.org.meeple.core.user.command.domain.User
import com.org.meeple.infra.user.command.mapper.toDomain
import com.org.meeple.infra.user.command.mapper.toEntity
import com.org.meeple.infra.user.command.repository.UserJpaRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 사용자 관련 아웃포트([GetUserPort], [SaveUserPort], [SoftDeleteUserPort], [RestoreUserPort], [AnonymizeUserPort])의 JPA 구현 어댑터.
 * 엔티티/도메인 변환을 책임지며, 외부에는 도메인 모델만 노출한다.
 */
@Component
class UserRepositoryAdapter(
	private val userJpaRepository: UserJpaRepository,
) : GetUserPort, SaveUserPort, SoftDeleteUserPort, RestoreUserPort, AnonymizeUserPort {

	override fun findByProviderAndProviderId(provider: String, providerId: String): User? =
		userJpaRepository.findByProviderAndProviderId(provider, providerId)?.toDomain()

	override fun findById(id: Long): User? =
		userJpaRepository.findById(id).orElse(null)?.toDomain()

	override fun existsByEmail(email: String): Boolean =
		userJpaRepository.existsByEmail(email)

	// id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge). 둘 다 Spring Data save가 처리한다.
	override fun save(user: User): User =
		userJpaRepository.save(user.toEntity()).toDomain()

	override fun softDelete(userId: Long, at: LocalDateTime) {
		userJpaRepository.softDeleteById(userId, at)
	}

	override fun findWithdrawnUserId(provider: String, providerId: String): Long? =
		userJpaRepository.findWithdrawnId(provider, providerId)

	override fun restore(userId: Long, at: LocalDateTime): User {
		userJpaRepository.restoreById(userId, at)
		// 복구 후에는 @SQLRestriction을 통과하므로 일반 조회로 도메인 모델을 읽는다.
		return userJpaRepository.findById(userId).orElseThrow().toDomain()
	}

	override fun anonymize(userId: Long, anonymizedProviderId: String): Boolean =
		userJpaRepository.anonymizeById(userId, anonymizedProviderId) > 0
}
