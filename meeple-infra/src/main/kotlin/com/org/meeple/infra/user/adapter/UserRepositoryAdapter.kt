package com.org.meeple.infra.user.adapter

import com.org.meeple.core.user.application.port.out.GetUserPort
import com.org.meeple.core.user.application.port.out.SaveUserPort
import com.org.meeple.core.user.domain.User
import com.org.meeple.infra.user.mapper.toDomain
import com.org.meeple.infra.user.mapper.toEntity
import com.org.meeple.infra.user.repository.UserJpaRepository
import org.springframework.stereotype.Component

/**
 * 사용자 아웃포트([GetUserPort], [SaveUserPort])의 JPA 구현 어댑터.
 * 엔티티/도메인 변환([UserMapper])을 책임지며, 외부에는 도메인 모델만 노출한다.
 */
@Component
class UserRepositoryAdapter(
	private val userJpaRepository: UserJpaRepository,
) : GetUserPort, SaveUserPort {

	override fun findByProviderAndProviderId(provider: String, providerId: String): User? =
		userJpaRepository.findByProviderAndProviderId(provider, providerId)?.toDomain()

	override fun findById(id: Long): User? =
		userJpaRepository.findById(id).orElse(null)?.toDomain()

	override fun existsByEmail(email: String): Boolean =
		userJpaRepository.existsByEmail(email)

	// id가 0이면 INSERT, 0이 아니면 기존 행 UPDATE(merge). 둘 다 Spring Data save가 처리한다.
	override fun save(user: User): User =
		userJpaRepository.save(user.toEntity()).toDomain()
}
