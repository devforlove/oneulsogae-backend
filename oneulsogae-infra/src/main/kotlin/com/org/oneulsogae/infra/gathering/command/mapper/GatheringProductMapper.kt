package com.org.oneulsogae.infra.gathering.command.mapper

import com.org.oneulsogae.admin.gathering.command.domain.GatheringProduct
import com.org.oneulsogae.infra.gathering.command.entity.GatheringProductEntity

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
