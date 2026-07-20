package com.org.oneulsogae.infra.gathering.command.mapper

import com.org.oneulsogae.core.gathering.command.domain.GatheringMember
import com.org.oneulsogae.infra.gathering.command.entity.GatheringMemberEntity

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
