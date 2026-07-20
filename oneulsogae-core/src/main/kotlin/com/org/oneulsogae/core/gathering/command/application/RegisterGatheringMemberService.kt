package com.org.oneulsogae.core.gathering.command.application

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.gathering.GatheringErrorCode
import com.org.oneulsogae.core.gathering.command.application.port.`in`.RegisterGatheringMemberUseCase
import com.org.oneulsogae.core.gathering.command.application.port.`in`.command.RegisterGatheringMemberCommand
import com.org.oneulsogae.core.gathering.command.application.port.`in`.result.RegisterGatheringMemberResult
import com.org.oneulsogae.core.gathering.command.application.port.out.GetJoiningSchedulePort
import com.org.oneulsogae.core.gathering.command.application.port.out.LoadGatheringMemberPort
import com.org.oneulsogae.core.gathering.command.application.port.out.SaveGatheringMemberPort
import com.org.oneulsogae.core.gathering.command.application.port.out.SaveJoiningSchedulePort
import com.org.oneulsogae.core.gathering.command.domain.GatheringMember
import com.org.oneulsogae.core.gathering.command.domain.JoinPricing
import com.org.oneulsogae.core.gathering.command.domain.JoiningSchedule
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [RegisterGatheringMemberUseCase] 구현.
 * 일정 행을 비관적 락으로 잠근 뒤 접수 규칙(판매 상태·성별 여분·중복 신청)을 검증하고,
 * 확정가 계산·여분 차감 후 승인대기(PENDING) 참가 행을 저장한다.
 * 거절/취소된 기존 행이 있으면 새 행 대신 되살린다((schedule_id, user_id) 유니크 제약).
 */
@Service
@Transactional
class RegisterGatheringMemberService(
	private val getJoiningSchedulePort: GetJoiningSchedulePort,
	private val saveJoiningSchedulePort: SaveJoiningSchedulePort,
	private val loadGatheringMemberPort: LoadGatheringMemberPort,
	private val saveGatheringMemberPort: SaveGatheringMemberPort,
) : RegisterGatheringMemberUseCase {

	override fun register(command: RegisterGatheringMemberCommand): RegisterGatheringMemberResult {
		val schedule: JoiningSchedule = getJoiningSchedulePort.getForUpdate(command.scheduleId)
			?.takeIf { it.gatheringId == command.gatheringId }
			?: throw BusinessException(GatheringErrorCode.GATHERING_SCHEDULE_NOT_FOUND)

		val existing: GatheringMember? = loadGatheringMemberPort.loadByScheduleIdAndUserId(command.scheduleId, command.userId)
		existing?.validateReRegistrable()

		val pricing: JoinPricing = schedule.register(command.gender, command.type)
		val member: GatheringMember = existing
			?.also { it.revive(gender = command.gender, earlyBirdApplied = pricing.earlyBirdApplied) }
			?: GatheringMember.pending(
				gatheringId = command.gatheringId,
				scheduleId = command.scheduleId,
				userId = command.userId,
				gender = command.gender,
				earlyBirdApplied = pricing.earlyBirdApplied,
			)

		val saved: GatheringMember = saveGatheringMemberPort.save(member)
		saveJoiningSchedulePort.save(schedule)
		return RegisterGatheringMemberResult(memberId = checkNotNull(saved.id), amount = pricing.amount)
	}
}
