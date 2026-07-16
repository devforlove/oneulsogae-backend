package com.org.meeple.infra.gathering.command.mapper

import com.org.meeple.core.gathering.command.domain.GatheringMember
import com.org.meeple.infra.gathering.command.entity.GatheringMemberEntity

/** [GatheringMemberEntity] ↔ core 도메인 [GatheringMember] 변환. */
fun GatheringMemberEntity.toDomain(): GatheringMember =
	GatheringMember(
		id = id,
		gatheringId = gatheringId,
		scheduleId = scheduleId,
		userId = userId,
		gender = gender,
		status = status,
		earlyBirdApplied = earlyBirdApplied,
	)

fun GatheringMember.toEntity(): GatheringMemberEntity =
	GatheringMemberEntity(
		gatheringId = gatheringId,
		scheduleId = scheduleId,
		userId = userId,
		gender = gender,
		status = status,
		earlyBirdApplied = earlyBirdApplied,
	)
