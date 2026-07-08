package com.org.meeple.admin.gathering.query.service

import com.org.meeple.admin.gathering.command.domain.GatheringDescriptionImage
import com.org.meeple.admin.gathering.query.service.port.`in`.GetGatheringDescriptionImageUrlUseCase
import com.org.meeple.admin.gathering.query.service.port.out.GatheringImageUrlPort
import org.springframework.stereotype.Service

@Service
class GetGatheringDescriptionImageUrlService(
	private val gatheringImageUrlPort: GatheringImageUrlPort,
) : GetGatheringDescriptionImageUrlUseCase {
	override fun execute(key: String): String? {
		if (!GatheringDescriptionImage.isValidKey(key)) return null
		return gatheringImageUrlPort.presignedGetUrl(key)
	}
}
