package com.org.meeple.infra.gathering.command.mapper

import com.org.meeple.admin.gathering.command.domain.AdminGathering
import com.org.meeple.admin.gathering.command.domain.GatheringFee
import com.org.meeple.infra.gathering.command.entity.GatheringEntity

fun GatheringEntity.toDomain(): AdminGathering =
	AdminGathering(
		id = id ?: 0,
		type = type,
		title = title,
		description = description,
		imageKey = imageKey,
		region = region,
		gatheringAt = gatheringAt,
		minParticipants = minParticipants,
		maxParticipants = maxParticipants,
		fee = GatheringFee(maleFee, femaleFee),
		earlyBirdFee = GatheringFee.optional(earlyBirdMaleFee, earlyBirdFemaleFee),
		earlyBirdCapacity = earlyBirdCapacity,
		discountFee = GatheringFee.optional(discountMaleFee, discountFemaleFee),
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
		gatheringAt = gatheringAt,
		minParticipants = minParticipants,
		maxParticipants = maxParticipants,
		maleFee = fee.male,
		femaleFee = fee.female,
		earlyBirdMaleFee = earlyBirdFee?.male,
		earlyBirdFemaleFee = earlyBirdFee?.female,
		earlyBirdCapacity = earlyBirdCapacity,
		discountMaleFee = discountFee?.male,
		discountFemaleFee = discountFee?.female,
		status = status,
	).also { if (id != 0L) it.id = id }
