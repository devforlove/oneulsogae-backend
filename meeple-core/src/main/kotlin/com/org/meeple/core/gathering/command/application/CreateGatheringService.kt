package com.org.meeple.core.gathering.command.application

import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.gathering.command.application.port.`in`.CreateGatheringUseCase
import com.org.meeple.core.gathering.command.application.port.`in`.command.CreateGatheringCommand
import com.org.meeple.core.gathering.command.application.port.out.SaveGatheringPort
import com.org.meeple.core.gathering.command.domain.Gathering
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class CreateGatheringService(
	private val saveGatheringPort: SaveGatheringPort,
	private val timeGenerator: TimeGenerator,
) : CreateGatheringUseCase {

	override fun create(command: CreateGatheringCommand): Gathering =
		saveGatheringPort.save(
			Gathering.create(
				userId = command.userId,
				type = command.type,
				title = command.title,
				description = command.description,
				gatheringAt = command.gatheringAt,
				capacity = command.capacity,
				now = timeGenerator.now(),
			),
		)
}
