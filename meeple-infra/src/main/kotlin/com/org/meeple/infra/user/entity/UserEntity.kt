package com.org.meeple.infra.user.entity

import com.org.meeple.common.user.Role
import com.org.meeple.common.user.UserStatus
import com.org.meeple.infra.common.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

/**
 * users 테이블 영속성 엔티티. 계정 식별 정보(provider/email/role/status)와 마지막 로그인 시점만 보관한다.
 * 닉네임/프로필 등 프로필 상세는 [UserDetailEntity]가 담당한다.
 * 도메인 로직을 두지 않고 상태만 보관하며, 도메인 행위는 [com.org.meeple.core.user.domain.User] 모델에 정의한다.
 */
@Entity
@SQLRestriction("deleted_at is null")
@Table(
	name = "users",
	uniqueConstraints = [UniqueConstraint(name = "udx_provider_provider_id", columnNames = ["provider", "provider_id"])],
	indexes = [
		// 매칭 배치/풀 그룹핑용. status 등치 + last_login_at 범위 seek + (lastLoginAt, id) 정렬/키셋을 filesort 없이 충족한다.
		Index(name = "idx_status_last_login_at_id", columnList = "status, last_login_at, id"),
	],
)
class UserEntity(
	@Column(name = "provider", nullable = false)
	val provider: String,

	@Column(name = "provider_id", nullable = false)
	val providerId: String,

	@Column(name = "email")
	var email: String? = null,

	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false)
	var role: Role = Role.USER,

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	var status: UserStatus = UserStatus.ONBOARDING,

	/** 마지막 로그인 시점. */
	@Column(name = "last_login_at")
	var lastLoginAt: LocalDateTime? = null,
) : BaseEntity()
