package com.org.meeple.infra.user.command.mapper

import com.org.meeple.core.user.command.domain.User
import com.org.meeple.infra.user.command.entity.UserEntity

/** 영속성 엔티티 -> 도메인 모델 */
fun UserEntity.toDomain(): User =
	User(
		id = id ?: 0,
		provider = provider,
		providerId = providerId,
		email = email,
		role = role,
		status = status,
		lastLoginAt = lastLoginAt,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규로 저장(INSERT)되고, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 */
fun User.toEntity(): UserEntity =
	UserEntity(
		provider = provider,
		providerId = providerId,
		email = email,
		role = role,
		status = status,
		lastLoginAt = lastLoginAt,
	).also { if (id != 0L) it.id = id }
