package com.org.meeple.admin.gathering.command.application

import com.org.meeple.admin.common.error.AdminErrorCode
import com.org.meeple.admin.common.error.AdminException
import com.org.meeple.admin.gathering.command.application.port.`in`.RejectGatheringMemberUseCase
import com.org.meeple.admin.gathering.command.application.port.out.ChangeGatheringMemberStatusPort
import com.org.meeple.admin.gathering.command.application.port.out.LoadAdminGatheringMemberPort
import com.org.meeple.admin.gathering.command.application.port.out.RestoreGatheringMemberSeatPort
import com.org.meeple.admin.gathering.command.domain.AdminGatheringMember
import com.org.meeple.common.gathering.GatheringMemberStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [RejectGatheringMemberUseCase] 구현. (명령)
 * 대상 신청을 로드해 없거나 [scheduleId] 일정 소속이 아니면 GATHERING_MEMBER_NOT_FOUND.
 * 전이 가능 여부는 도메인([AdminGatheringMember.validateRejectable])이 판정하고,
 * 통과 시 거절(REJECTED)로 전이하며 접수 시 차감한 일정 여분(성별·얼리버드)을 같은 트랜잭션에서 복원한다.
 */
@Service
@Transactional
class RejectGatheringMemberService(
	private val loadAdminGatheringMemberPort: LoadAdminGatheringMemberPort,
	private val changeGatheringMemberStatusPort: ChangeGatheringMemberStatusPort,
	private val restoreGatheringMemberSeatPort: RestoreGatheringMemberSeatPort,
) : RejectGatheringMemberUseCase {

	override fun reject(scheduleId: Long, memberId: Long) {
		val member: AdminGatheringMember = loadAdminGatheringMemberPort.loadById(memberId)
			?.takeIf { it.scheduleId == scheduleId }
			?: throw AdminException(AdminErrorCode.GATHERING_MEMBER_NOT_FOUND, "모임 참가 신청을 찾을 수 없습니다: $memberId")
		member.validateRejectable()
		changeGatheringMemberStatusPort.changeStatus(memberId, GatheringMemberStatus.REJECTED)
		restoreGatheringMemberSeatPort.restore(member.scheduleId, member.gender, member.earlyBirdApplied)
	}
}
