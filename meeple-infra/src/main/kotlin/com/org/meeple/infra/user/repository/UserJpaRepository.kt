package com.org.meeple.infra.user.repository

import com.org.meeple.infra.user.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 사용자 영속성 엔티티에 대한 Spring Data JPA 리포지토리.
 * 도메인 포트([com.org.meeple.core.user.application.port.out.GetUserPort])는 [UserRepositoryAdapter]가 구현한다.
 */
interface UserJpaRepository : JpaRepository<UserEntity, Long> {

	fun findByProviderAndProviderId(provider: String, providerId: String): UserEntity?

	fun existsByEmail(email: String): Boolean
}
