package com.org.oneulsogae.core.teammatch.command.application

import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.common.event.DomainEventPublisher
import com.org.oneulsogae.core.common.lock.DistributedLock
import com.org.oneulsogae.core.common.lock.LockKeyConstraints
import com.org.oneulsogae.core.common.time.TimeGenerator
import com.org.oneulsogae.core.teammatch.TeamErrorCode
import com.org.oneulsogae.core.teammatch.command.application.port.`in`.WithdrawTeamInvitationUseCase
import com.org.oneulsogae.core.teammatch.command.application.port.out.GetTeamPort
import com.org.oneulsogae.core.teammatch.command.application.port.out.SaveTeamPort
import com.org.oneulsogae.core.teammatch.command.domain.Team
import com.org.oneulsogae.core.teammatch.command.domain.event.TeamInvitationCanceled
import com.org.oneulsogae.core.teammatch.command.domain.event.TeamInvitationDeclined
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [WithdrawTeamInvitationUseCase] 구현. INVITING 팀의 초대를 철회(거절/취소)한다.
 * 팀을 조회해 [Team.withdrawInvitation]으로 비활성화(DEACTIVATED + soft delete)한 뒤 저장한다.
 * 철회 주체에 따라 거절([TeamInvitationDeclined], 초대받은 사람) / 취소([TeamInvitationCanceled], 초대자) 이벤트를 구분 발행해
 * 상대에게 알람이 가도록 한다. (커밋 이후 핸들러가 처리)
 * 비활성화 시각은 [TimeGenerator]로 얻어 도메인에 주입한다. (LocalDateTime.now() 직접 호출 금지 — 테스트에서 시각 고정 가능)
 * teamId 분산 락으로 수락 등 동시 상태 변경과 직렬화한다.
 */
@Service
class WithdrawTeamInvitationService(
	private val getTeamPort: GetTeamPort,
	private val saveTeamPort: SaveTeamPort,
	private val timeGenerator: TimeGenerator,
	private val domainEventPublisher: DomainEventPublisher,
) : WithdrawTeamInvitationUseCase {

	@DistributedLock(prefix = LockKeyConstraints.TEAM_LIFECYCLE, keys = ["#teamId"], waitTime = 0)
	@Transactional
	override fun withdraw(userId: Long, teamId: Long): Team {
		val team: Team = getTeamPort.findById(teamId)
			?: throw BusinessException(TeamErrorCode.TEAM_NOT_FOUND)
		val withdrawn: Team = saveTeamPort.save(team.withdrawInvitation(userId, timeGenerator.now()))
		publishWithdrawn(team, withdrawnBy = userId)
		return withdrawn
	}

	/**
	 * 철회 주체([withdrawnBy])가 초대받은 사람이면 거절, 초대자면 취소로 보고 상대에게 보낼 이벤트를 발행한다.
	 * 역할은 철회 직전(INVITING) 팀의 구성원 상태에서 읽는다.
	 */
	private fun publishWithdrawn(team: Team, withdrawnBy: Long) {
		val inviterId: Long = team.inviterId()
		val invitedId: Long = team.invitedId()
		if (withdrawnBy == invitedId) {
			domainEventPublisher.publish(TeamInvitationDeclined(team.id, inviterUserId = inviterId, invitedUserId = invitedId))
		} else {
			domainEventPublisher.publish(TeamInvitationCanceled(team.id, inviterUserId = inviterId, invitedUserId = invitedId))
		}
	}
}
