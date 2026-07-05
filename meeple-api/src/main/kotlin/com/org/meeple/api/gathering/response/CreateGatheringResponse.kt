package com.org.meeple.api.gathering.response

import com.org.meeple.core.gathering.command.domain.Gathering

data class CreateGatheringResponse(
	val gatheringId: Long,
) {
	companion object {
		fun of(gathering: Gathering): CreateGatheringResponse =
			CreateGatheringResponse(gatheringId = gathering.id)
	}
}
