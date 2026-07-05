package com.org.meeple.core.gathering.command.application.port.`in`.command

import com.org.meeple.common.gathering.GatheringType
import java.time.LocalDateTime

data class CreateGatheringCommand(
	val userId: Long?,
	val type: GatheringType,
	val title: String,
	val description: String?,
	val gatheringAt: LocalDateTime,
	val capacity: Int,
)
