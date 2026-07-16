package com.org.meeple.infra.gathering.command.mapper

import com.org.meeple.admin.gathering.command.domain.GatheringProduct
import com.org.meeple.infra.gathering.command.entity.GatheringProductEntity

fun GatheringProductEntity.toDomain(): GatheringProduct =
	GatheringProduct(
		id = id ?: 0,
		gatheringId = gatheringId,
		scheduleId = scheduleId,
		gender = gender,
		type = type,
		price = price,
	)

fun GatheringProduct.toEntity(): GatheringProductEntity =
	GatheringProductEntity(
		gatheringId = gatheringId,
		scheduleId = scheduleId,
		gender = gender,
		type = type,
		price = price,
	).also { if (id != 0L) it.id = id }
