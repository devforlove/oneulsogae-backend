package com.org.meeple.infra.gathering.command.mapper

import com.org.meeple.admin.gathering.command.domain.GatheringSchedule
import com.org.meeple.infra.gathering.command.entity.GatheringScheduleEntity

fun GatheringScheduleEntity.toDomain(): GatheringSchedule =
	GatheringSchedule(
		id = id ?: 0,
		gatheringId = gatheringId,
		startAt = startAt,
		endAt = endAt,
		status = status,
	)

fun GatheringSchedule.toEntity(): GatheringScheduleEntity =
	GatheringScheduleEntity(
		gatheringId = gatheringId,
		startAt = startAt,
		endAt = endAt,
		status = status,
	).also { if (id != 0L) it.id = id }
