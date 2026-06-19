package com.org.meeple.infra.match.command.mapper

import com.org.meeple.core.match.command.domain.MatchUser
import com.org.meeple.infra.match.command.entity.MatchUserEntity

/** 영속성 엔티티 -> 도메인 모델. */
fun MatchUserEntity.toDomain(): MatchUser =
	MatchUser(
		userId = userId,
		gender = gender,
		age = age,
		regionCode = regionCode,
		maritalStatus = maritalStatus,
		nickname = nickname,
		lastLoginAt = lastLoginAt,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * user_id가 PK(할당 식별자)이므로 save 시 없으면 INSERT, 있으면 UPDATE(merge)로 동작한다(upsert).
 * created_at은 updatable=false라 갱신 경로의 UPDATE에서 제외되어 보존된다.
 */
fun MatchUser.toEntity(): MatchUserEntity =
	MatchUserEntity(
		userId = userId,
		gender = gender,
		regionCode = regionCode,
		maritalStatus = maritalStatus,
		age = age,
		nickname = nickname,
		lastLoginAt = lastLoginAt,
	)
