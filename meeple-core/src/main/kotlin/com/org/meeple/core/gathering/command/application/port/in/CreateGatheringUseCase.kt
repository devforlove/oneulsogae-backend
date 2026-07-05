package com.org.meeple.core.gathering.command.application.port.`in`

import com.org.meeple.core.gathering.command.application.port.`in`.command.CreateGatheringCommand
import com.org.meeple.core.gathering.command.domain.Gathering

interface CreateGatheringUseCase {

	fun create(command: CreateGatheringCommand): Gathering
}
