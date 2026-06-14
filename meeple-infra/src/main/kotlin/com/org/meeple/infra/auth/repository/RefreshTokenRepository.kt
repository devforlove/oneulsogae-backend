package com.org.meeple.infra.auth.repository

import com.org.meeple.infra.auth.entity.RefreshTokenEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RefreshTokenRepository : JpaRepository<RefreshTokenEntity, Long> {

	fun findByTokenId(tokenId: String): RefreshTokenEntity?

	/** 재사용 탐지 시 해당 사용자의 모든 토큰을 일괄 폐기한다. */
	@Modifying(clearAutomatically = true)
	@Query("update RefreshTokenEntity r set r.revoked = true where r.userId = :userId and r.revoked = false")
	fun revokeAllByUserId(@Param("userId") userId: Long): Int
}
