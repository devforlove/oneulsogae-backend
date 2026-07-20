package com.org.oneulsogae.infra.user.command.repository

import com.org.oneulsogae.infra.user.command.entity.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

/**
 * 사용자 영속성 엔티티에 대한 Spring Data JPA 리포지토리.
 * 도메인 포트([com.org.oneulsogae.core.user.command.application.port.out.GetUserPort])는 [UserRepositoryAdapter]가 구현한다.
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

	/** 복구: deleted_at 해제 + last_login_at 갱신. deleted_at이 null이면(이미 활성) 가드로 건너뛴다. */
	@Modifying(clearAutomatically = true)
	@Query(value = "update users set deleted_at = null, last_login_at = :now where id = :id and deleted_at is not null", nativeQuery = true)
	fun restoreById(@Param("id") id: Long, @Param("now") now: LocalDateTime): Int

	/**
	 * 파기: users 익명화. (소프트삭제 행 대상 → 네이티브)
	 * deleted_at이 null(복구된 활성 계정)이거나 이미 WITHDRAWN이면 0행 반환해 멱등을 보장한다.
	 */
	@Modifying(clearAutomatically = true)
	@Query(value = "update users set email = null, provider_id = :providerId, status = 'WITHDRAWN' where id = :id and deleted_at is not null and status <> 'WITHDRAWN'", nativeQuery = true)
	fun anonymizeById(@Param("id") id: Long, @Param("providerId") providerId: String): Int

	/** 파기 대상: 유예 경과(deleted_at < cutoff) + 아직 미익명화(status <> WITHDRAWN). 소프트삭제 행이라 네이티브. */
	@Query(value = "select id from users where deleted_at is not null and deleted_at < :cutoff and status <> 'WITHDRAWN'", nativeQuery = true)
	fun findPurgableUserIds(@Param("cutoff") cutoff: LocalDateTime): List<Long>
}
