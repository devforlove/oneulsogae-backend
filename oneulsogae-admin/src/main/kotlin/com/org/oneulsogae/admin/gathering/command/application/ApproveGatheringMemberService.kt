package com.org.oneulsogae.admin.gathering.command.application

import com.org.oneulsogae.admin.common.error.AdminErrorCode
import com.org.oneulsogae.admin.common.error.AdminException
import com.org.oneulsogae.admin.gathering.command.application.port.`in`.ApproveGatheringMemberUseCase
import com.org.oneulsogae.admin.gathering.command.application.port.out.ChangeGatheringMemberStatusPort
import com.org.oneulsogae.admin.gathering.command.application.port.out.ExistsGatheringProfilePort
import com.org.oneulsogae.admin.gathering.command.application.port.out.LoadAdminGatheringMemberPort
import com.org.oneulsogae.admin.gathering.command.domain.AdminGatheringMember
import com.org.oneulsogae.common.gathering.GatheringMemberStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [ApproveGatheringMemberUseCase] 구현. (명령)
 * 대상 신청을 로드해 없거나 [scheduleId] 일정 소속이 아니면 GATHERING_MEMBER_NOT_FOUND.
 * 전이 가능 여부는 도메인([AdminGatheringMember.validateApprovable])이 판정한다 — 승인대기 상태여야 하고,
 * 회원 인증(gathering_profile)을 마친 유저여야 한다([ExistsGatheringProfilePort]). 통과 시 참가(JOINED)로 전이한다.
 * (승인은 여분을 바꾸지 않는다 — 접수 시점에 이미 차감됨)
 */
@Service
@Transactional
class ApproveGatheringMemberService(
	private val loadAdminGatheringMemberPort: LoadAdminGatheringMemberPort,
	private val changeGatheringMemberStatusPort: ChangeGatheringMemberStatusPort,
	private val existsGatheringProfilePort: ExistsGatheringProfilePort,
) : ApproveGatheringMemberUseCase {

	override fun approve(scheduleId: Long, memberId: Long) {
		val member: AdminGatheringMember = loadAdminGatheringMemberPort.loadById(memberId)
			?.takeIf { it.scheduleId == scheduleId }
			?: throw AdminException(AdminErrorCode.GATHERING_MEMBER_NOT_FOUND, "모임 참가 신청을 찾을 수 없습니다: $memberId")
		member.validateApprovable(existsGatheringProfilePort.existsByUserId(member.userId))
		changeGatheringMemberStatusPort.changeStatus(memberId, GatheringMemberStatus.JOINED)
	}
}
