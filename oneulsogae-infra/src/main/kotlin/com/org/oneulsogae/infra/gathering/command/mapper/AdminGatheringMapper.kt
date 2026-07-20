package com.org.oneulsogae.infra.gathering.command.mapper

import com.org.oneulsogae.admin.gathering.command.domain.AdminGathering
import com.org.oneulsogae.infra.gathering.command.entity.GatheringEntity

fun GatheringEntity.toDomain(): AdminGathering =
	AdminGathering(
		id = id ?: 0,
		type = type,
		title = title,
		description = description,
		imageKey = imageKey,
		region = region,
		minParticipants = minParticipants,
		maxParticipants = maxParticipants,
		status = status,
	)

fun AdminGathering.toEntity(): GatheringEntity =
	GatheringEntity(
		type = type,
		// 운영(어드민) 생성이므로 생성자 user_id는 null이다.
		userId = null,
		title = title,
		description = description,
		imageKey = imageKey,
		region = region,
		minParticipants = minParticipants,
		maxParticipants = maxParticipants,
		status = status,
	).also { if (id != 0L) it.id = id }
