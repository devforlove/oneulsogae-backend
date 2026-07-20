package com.org.oneulsogae.api.admin.response

import com.org.oneulsogae.admin.gathering.command.domain.AdminGathering

data class CreateAdminGatheringResponse(
	val gatheringId: Long,
) {
	companion object {
		fun of(gathering: AdminGathering): CreateAdminGatheringResponse =
			CreateAdminGatheringResponse(gatheringId = gathering.id)
	}
}
