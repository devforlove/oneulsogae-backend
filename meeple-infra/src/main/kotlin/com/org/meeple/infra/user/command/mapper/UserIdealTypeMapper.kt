package com.org.meeple.infra.user.command.mapper

import com.org.meeple.core.user.command.domain.UserIdealType
import com.org.meeple.infra.user.command.entity.UserIdealTypeEntity

/** 영속성 엔티티 -> 도메인 모델 */
fun UserIdealTypeEntity.toDomain(): UserIdealType =
	UserIdealType(
		id = id ?: 0,
		userId = userId,
		ageMin = ageMin,
		ageMax = ageMax,
		heightMin = heightMin,
		heightMax = heightMax,
		maritalStatus = maritalStatus,
		smokingStatus = smokingStatus,
		drinkingStatus = drinkingStatus,
		religion = religion,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규 저장(INSERT), 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 */
fun UserIdealType.toEntity(): UserIdealTypeEntity =
	UserIdealTypeEntity(
		userId = userId,
		ageMin = ageMin,
		ageMax = ageMax,
		heightMin = heightMin,
		heightMax = heightMax,
		maritalStatus = maritalStatus,
		smokingStatus = smokingStatus,
		drinkingStatus = drinkingStatus,
		religion = religion,
	).also { if (id != 0L) it.id = id }
