package com.org.meeple.infra.gathering.command.mapper

import com.org.meeple.core.gathering.command.domain.Gathering
import com.org.meeple.infra.gathering.command.entity.GatheringEntity

fun GatheringEntity.toDomain(): Gathering =
	Gathering(
		id = id ?: 0,
		type = type,
		userId = userId,
		title = title,
		description = description,
		regionId = regionId,
		gatheringAt = gatheringAt,
		capacity = capacity,
		fee = fee,
		status = status,
	)

fun Gathering.toEntity(): GatheringEntity =
	GatheringEntity(
		type = type,
		userId = userId,
		title = title,
		description = description,
		regionId = regionId,
		gatheringAt = gatheringAt,
		capacity = capacity,
		fee = fee,
		status = status,
	).also { if (id != 0L) it.id = id }
