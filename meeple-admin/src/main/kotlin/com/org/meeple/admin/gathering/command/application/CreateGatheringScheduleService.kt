package com.org.meeple.admin.gathering.command.application

import com.org.meeple.admin.common.error.AdminErrorCode
import com.org.meeple.admin.common.error.AdminException
import com.org.meeple.admin.common.time.TimeGenerator
import com.org.meeple.admin.gathering.command.application.port.`in`.CreateGatheringScheduleUseCase
import com.org.meeple.admin.gathering.command.application.port.`in`.command.CreateGatheringScheduleCommand
import com.org.meeple.admin.gathering.command.application.port.out.GetAdminGatheringPort
import com.org.meeple.admin.gathering.command.application.port.out.SaveGatheringSchedulePort
import com.org.meeple.admin.gathering.command.domain.GatheringSchedule
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [CreateGatheringScheduleUseCase] 구현. [command]로 모임 일정([GatheringSchedule])을 만들어 저장한다.
 * 대상 모임이 존재하는지 [getAdminGatheringPort]로 먼저 확인하고(없으면 GATHERING_NOT_FOUND),
 * 시간 범위 검증은 [timeGenerator] 기준으로 도메인이 수행한다.
 */
@Service
@Transactional
class CreateGatheringScheduleService(
	private val getAdminGatheringPort: GetAdminGatheringPort,
	private val saveGatheringSchedulePort: SaveGatheringSchedulePort,
	private val timeGenerator: TimeGenerator,
) : CreateGatheringScheduleUseCase {

	override fun create(command: CreateGatheringScheduleCommand): GatheringSchedule {
		getAdminGatheringPort.findById(command.gatheringId)
			?: throw AdminException(AdminErrorCode.GATHERING_NOT_FOUND, "모임을 찾을 수 없습니다: ${command.gatheringId}")
		return saveGatheringSchedulePort.save(
			GatheringSchedule.create(
				gatheringId = command.gatheringId,
				startAt = command.startAt,
				endAt = command.endAt,
				now = timeGenerator.now(),
			),
		)
	}
}
