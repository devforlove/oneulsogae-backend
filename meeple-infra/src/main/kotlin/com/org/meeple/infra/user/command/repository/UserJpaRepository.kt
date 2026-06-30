package com.org.meeple.infra.user.command.repository

import com.org.meeple.infra.user.command.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

/**
 * 사용자 영속성 엔티티에 대한 Spring Data JPA 리포지토리.
 * 도메인 포트([com.org.meeple.core.user.command.application.port.out.GetUserPort])는 [UserRepositoryAdapter]가 구현한다.
 */
interface UserJpaRepository : JpaRepository<UserEntity, Long> {

	fun findByProviderAndProviderId(provider: String, providerId: String): UserEntity?

	fun existsByEmail(email: String): Boolean

	/** 소프트삭제(탈퇴): deleted_at을 설정한다. @SQLRestriction 우회를 위해 네이티브. */
	@Modifying(clearAutomatically = true)
	@Query(value = "update users set deleted_at = :now where id = :id and deleted_at is null", nativeQuery = true)
	fun softDeleteById(@Param("id") id: Long, @Param("now") now: LocalDateTime): Int

	/** 소프트삭제 포함 조회: 탈퇴 유예중(원본 provider_id 잔존) 사용자 id. */
	@Query(value = "select id from users where provider = :provider and provider_id = :providerId and deleted_at is not null", nativeQuery = true)
	fun findWithdrawnId(@Param("provider") provider: String, @Param("providerId") providerId: String): Long?

	/** 복구: deleted_at 해제 + last_login_at 갱신. */
	@Modifying(clearAutomatically = true)
	@Query(value = "update users set deleted_at = null, last_login_at = :now where id = :id", nativeQuery = true)
	fun restoreById(@Param("id") id: Long, @Param("now") now: LocalDateTime): Int

	/** 파기: users 익명화. (소프트삭제 행 대상 → 네이티브) */
	@Modifying(clearAutomatically = true)
	@Query(value = "update users set email = null, provider_id = :providerId, status = 'WITHDRAWN' where id = :id", nativeQuery = true)
	fun anonymizeById(@Param("id") id: Long, @Param("providerId") providerId: String): Int
}
