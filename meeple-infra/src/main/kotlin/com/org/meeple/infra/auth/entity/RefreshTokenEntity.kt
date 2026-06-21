package com.org.meeple.infra.auth.entity

import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

/**
 * 발급된 refresh token을 추적하는 영속성 엔티티.
 * JWT의 jti(tokenId)만 저장하므로 토큰 평문은 DB에 남지 않는다.
 * - 회전(rotation): 사용된 토큰은 revoked 처리하고 새 jti를 저장한다.
 * - 재사용 탐지: 이미 revoked된 jti가 다시 들어오면 탈취로 간주한다.
 * - 폐기(로그아웃): 해당 jti를 revoked 처리한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "refresh_tokens",
	indexes = [
		Index(name = "ux_token_id", columnList = "token_id", unique = true),
		Index(name = "idx_user_id", columnList = "user_id"),
	],
)
class RefreshTokenEntity(
	@Column(name = "token_id", nullable = false, length = 64)
	val tokenId: String,

	@Column(name = "user_id", nullable = false)
	val userId: Long,

	@Column(name = "expires_at", nullable = false)
	val expiresAt: LocalDateTime,

	@Column(name = "revoked", nullable = false)
	var revoked: Boolean = false,
) : BaseEntity() {

	val isActive: Boolean
		get() = !revoked && expiresAt.isAfter(LocalDateTime.now())

	/** 회전/로그아웃 시 토큰을 폐기한다. */
	fun revoke() {
		this.revoked = true
	}
}
