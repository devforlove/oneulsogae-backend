package com.org.oneulsogae.admin.gathering.command.application

import com.org.oneulsogae.admin.common.error.AdminErrorCode
import com.org.oneulsogae.admin.common.error.AdminException
import com.org.oneulsogae.admin.gathering.command.application.port.`in`.ChangeGatheringScheduleStatusUseCase
import com.org.oneulsogae.admin.gathering.command.application.port.out.ChangeGatheringScheduleStatusPort
import com.org.oneulsogae.admin.gathering.command.application.port.out.LoadGatheringSchedulePort
import com.org.oneulsogae.admin.gathering.command.domain.GatheringSchedule
import com.org.oneulsogae.common.gathering.GatheringScheduleStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [ChangeGatheringScheduleStatusUseCase] 구현. (명령)
 * 대상 일정을 로드해 없거나 [gatheringId] 모임 소속이 아니면 GATHERING_SCHEDULE_NOT_FOUND.
 * 전이 가능 여부는 도메인([GatheringSchedule.changeStatus])이 판정하고, 통과 시 status를 전이한다.
 */
@Service
@Transactional
class ChangeGatheringScheduleStatusService(
	private val loadGatheringSchedulePort: LoadGatheringSchedulePort,
	private val changeGatheringScheduleStatusPort: ChangeGatheringScheduleStatusPort,
) : ChangeGatheringScheduleStatusUseCase {

	override fun changeStatus(gatheringId: Long, scheduleId: Long, status: GatheringScheduleStatus) {
		val schedule: GatheringSchedule = loadGatheringSchedulePort.loadById(scheduleId)
			?.takeIf { it.gatheringId == gatheringId }
			?: throw AdminException(AdminErrorCode.GATHERING_SCHEDULE_NOT_FOUND, "모임 일정을 찾을 수 없습니다: $scheduleId")
		schedule.changeStatus(status)
		changeGatheringScheduleStatusPort.changeStatus(scheduleId, status)
	}
}
