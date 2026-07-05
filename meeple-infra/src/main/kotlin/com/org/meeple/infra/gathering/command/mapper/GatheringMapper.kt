package com.org.meeple.infra.gathering.command.mapper

import com.org.meeple.core.gathering.command.domain.Gathering
import com.org.meeple.core.gathering.command.domain.GatheringFee
import com.org.meeple.infra.gathering.command.entity.GatheringEntity

fun GatheringEntity.toDomain(): Gathering =
	Gathering(
		id = id ?: 0,
		type = type,
		userId = userId,
		title = title,
		description = description,
		region = region,
		gatheringAt = gatheringAt,
		capacity = capacity,
		fee = GatheringFee(maleFee, femaleFee),
		earlyBirdFee = GatheringFee.optional(earlyBirdMaleFee, earlyBirdFemaleFee),
		discountFee = GatheringFee.optional(discountMaleFee, discountFemaleFee),
		status = status,
	)

fun Gathering.toEntity(): GatheringEntity =
	GatheringEntity(
		type = type,
		userId = userId,
		title = title,
		description = description,
		region = region,
		gatheringAt = gatheringAt,
		capacity = capacity,
		maleFee = fee.male,
		femaleFee = fee.female,
		earlyBirdMaleFee = earlyBirdFee?.male,
		earlyBirdFemaleFee = earlyBirdFee?.female,
		discountMaleFee = discountFee?.male,
		discountFemaleFee = discountFee?.female,
		status = status,
	).also { if (id != 0L) it.id = id }
