package com.org.meeple.api.admin.response

import com.org.meeple.admin.gathering.command.domain.AdminGathering

data class CreateAdminGatheringResponse(
	val gatheringId: Long,
) {
	companion object {
		fun of(gathering: AdminGathering): CreateAdminGatheringResponse =
			CreateAdminGatheringResponse(gatheringId = gathering.id)
	}
}
