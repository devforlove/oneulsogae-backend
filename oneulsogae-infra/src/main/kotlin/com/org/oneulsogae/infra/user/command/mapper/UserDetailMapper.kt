package com.org.oneulsogae.infra.user.command.mapper

import com.org.oneulsogae.core.user.command.domain.UserDetail
import com.org.oneulsogae.infra.user.command.entity.UserDetailEntity

/** 영속성 엔티티 -> 도메인 모델 */
fun UserDetailEntity.toDomain(): UserDetail =
	UserDetail(
		id = id ?: 0,
		userId = userId,
		nickname = nickname,
		profileImageCode = profileImageCode,
		birthday = birthday,
		height = height,
		gender = gender,
		phoneNumber = phoneNumber,
		job = job,
		regionId = regionId,
		introduction = introduction,
		traits = traits,
		interests = interests,
		companyEmail = companyEmail,
		companyName = companyName,
		universityEmail = universityEmail,
		universityName = universityName,
		secondaryEmail = secondaryEmail,
		maritalStatus = maritalStatus,
		smokingStatus = smokingStatus,
		religion = religion,
		drinkingStatus = drinkingStatus,
		bodyType = bodyType,
	)

/**
 * 도메인 모델 -> 영속성 엔티티.
 * id가 0이면 신규로 저장(INSERT)되고, 0이 아니면 기존 행으로 식별돼 save 시 갱신(merge)된다.
 */
fun UserDetail.toEntity(): UserDetailEntity =
	UserDetailEntity(
		userId = userId,
		nickname = nickname,
		profileImageCode = profileImageCode,
		birthday = birthday,
		height = height,
		gender = gender,
		phoneNumber = phoneNumber,
		job = job,
		regionId = regionId,
		introduction = introduction,
		traits = traits,
		interests = interests,
		companyEmail = companyEmail,
		companyName = companyName,
		universityEmail = universityEmail,
		universityName = universityName,
		secondaryEmail = secondaryEmail,
		maritalStatus = maritalStatus,
		smokingStatus = smokingStatus,
		religion = religion,
		drinkingStatus = drinkingStatus,
		bodyType = bodyType,
	).also { if (id != 0L) it.id = id }
