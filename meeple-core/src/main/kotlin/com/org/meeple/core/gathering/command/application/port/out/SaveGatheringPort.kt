package com.org.meeple.core.gathering.command.application.port.out

import com.org.meeple.core.gathering.command.domain.Gathering

interface SaveGatheringPort {

	fun save(gathering: Gathering): Gathering
}
