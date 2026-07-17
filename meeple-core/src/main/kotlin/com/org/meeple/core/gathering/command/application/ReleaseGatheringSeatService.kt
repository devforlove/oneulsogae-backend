package com.org.meeple.core.gathering.command.application

import com.org.meeple.core.gathering.command.application.port.`in`.ReleaseGatheringSeatUseCase
import com.org.meeple.core.gathering.command.application.port.out.GetJoiningSchedulePort
import com.org.meeple.core.gathering.command.application.port.out.LoadGatheringMemberPort
import com.org.meeple.core.gathering.command.application.port.out.SaveGatheringMemberPort
import com.org.meeple.core.gathering.command.application.port.out.SaveJoiningSchedulePort
import com.org.meeple.core.gathering.command.domain.GatheringMember
import com.org.meeple.core.gathering.command.domain.JoiningSchedule
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [ReleaseGatheringSeatUseCase] 구현. (명령 보상)
 * 접수 시 잠근 것과 같은 경로(getForUpdate)로 일정을 잠그고 여분을 복원한 뒤 참가를 취소한다.
 * 방금 접수한 직후 호출되므로 대상 행이 없으면 시스템 오류로 본다.
 */
@Service
@Transactional
class ReleaseGatheringSeatService(
	private val loadGatheringMemberPort: LoadGatheringMemberPort,
	private val saveGatheringMemberPort: SaveGatheringMemberPort,
	private val getJoiningSchedulePort: GetJoiningSchedulePort,
	private val saveJoiningSchedulePort: SaveJoiningSchedulePort,
) : ReleaseGatheringSeatUseCase {

	override fun release(scheduleId: Long, userId: Long) {
		val member: GatheringMember = loadGatheringMemberPort.loadByScheduleIdAndUserId(scheduleId, userId)
			?: throw IllegalStateException("복원할 참가자를 찾을 수 없습니다: schedule=$scheduleId, user=$userId")
		val schedule: JoiningSchedule = getJoiningSchedulePort.getForUpdate(member.scheduleId)
			?: throw IllegalStateException("모임 일정을 찾을 수 없습니다: ${member.scheduleId}")
		schedule.restore(member.gender, member.earlyBirdApplied)
		member.cancel()
		saveGatheringMemberPort.save(member)
		saveJoiningSchedulePort.save(schedule)
	}
}
