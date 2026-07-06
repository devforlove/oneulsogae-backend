package com.org.meeple.infra.gathering.command.mapper

import com.org.meeple.admin.gathering.command.domain.GatheringFee
import com.org.meeple.admin.gathering.command.domain.GatheringSchedule
import com.org.meeple.infra.gathering.command.entity.GatheringScheduleEntity

fun GatheringScheduleEntity.toDomain(): GatheringSchedule =
	GatheringSchedule(
		id = id ?: 0,
		gatheringId = gatheringId,
		startAt = startAt,
		endAt = endAt,
		fee = GatheringFee(maleFee, femaleFee),
		earlyBirdFee = GatheringFee.optional(earlyBirdMaleFee, earlyBirdFemaleFee),
		earlyBirdCapacity = earlyBirdCapacity,
		discountFee = GatheringFee.optional(discountMaleFee, discountFemaleFee),
		status = status,
	)

fun GatheringSchedule.toEntity(): GatheringScheduleEntity =
	GatheringScheduleEntity(
		gatheringId = gatheringId,
		startAt = startAt,
		endAt = endAt,
		maleFee = fee.male,
		femaleFee = fee.female,
		earlyBirdMaleFee = earlyBirdFee?.male,
		earlyBirdFemaleFee = earlyBirdFee?.female,
		earlyBirdCapacity = earlyBirdCapacity,
		// 저장 시 남은 개수는 정원(earlyBirdCapacity)으로 초기화한다.
		earlyBirdRemaining = earlyBirdCapacity,
		discountMaleFee = discountFee?.male,
		discountFemaleFee = discountFee?.female,
		status = status,
	).also { if (id != 0L) it.id = id }
