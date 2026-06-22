package com.org.meeple.core.match.command.application

import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.common.event.DomainEventPublisher
import com.org.meeple.core.common.lock.DistributedLock
import com.org.meeple.core.common.lock.LockKeyConstraints
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.match.TeamErrorCode
import com.org.meeple.core.match.command.application.port.`in`.AcceptTeamInvitationUseCase
import com.org.meeple.core.match.command.application.port.out.GetTeamPort
import com.org.meeple.core.match.command.application.port.out.SaveTeamPort
import com.org.meeple.core.match.command.domain.Team
import com.org.meeple.core.match.command.domain.event.TeamInvitationAccepted
import com.org.meeple.core.match.command.domain.event.TeamInvitationCanceled
import com.org.meeple.core.match.command.domain.event.TeamInvitationDeclined
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [AcceptTeamInvitationUseCase] 구현. 초대받은 사용자가 팀 초대를 수락한다.
 * 팀을 조회해 [Team.acceptInvitation]으로 상태를 전이(전원 수락 시 ACTIVE)한 뒤 저장한다.
 * 수락과 동시에 그 사용자가 받은 다른 초대들은 모두 비활성화한다. (한 초대 수락 = 나머지 초대 자동 거절)
 * 수락(invited)↔초대취소(owner) 동시 요청 경합을 막기 위해 teamId 분산 락으로 직렬화한다. (waitTime=0)
 */
@Service
class AcceptTeamInvitationService(
	private val getTeamPort: GetTeamPort,
	private val saveTeamPort: SaveTeamPort,
	private val timeGenerator: TimeGenerator,
	private val domainEventPublisher: DomainEventPublisher,
) : AcceptTeamInvitationUseCase {

	@DistributedLock(prefix = LockKeyConstraints.TEAM_LIFECYCLE, keys = ["#teamId"], waitTime = 0)
	@Transactional
	override fun accept(userId: Long, teamId: Long): Team {
		val team: Team = getTeamPort.findById(teamId)
			?: throw BusinessException(TeamErrorCode.TEAM_NOT_FOUND)
		val accepted: Team = saveTeamPort.save(team.acceptInvitation(userId))
		deactivateOtherInvitations(userId, teamId)
		// 초대받은 사람이 수락 → 초대했던 사람에게 알람이 가도록 이벤트 발행. (커밋 이후 핸들러가 처리)
		// 초대자는 수락 직전(INVITING — 유일한 ACTIVE) 팀에서 읽는다. 수락 후엔 전원 ACTIVE라 초대자/수락자를 구분할 수 없다.
		domainEventPublisher.publish(
			TeamInvitationAccepted(accepted.id, inviterUserId = team.inviterId(), invitedUserId = userId),
		)
		return accepted
	}

	/**
	 * [userId]가 구성원인 진행 중(INVITING) 팀 중 방금 수락한 [acceptedTeamId] 외의 팀들을 모두 비활성화한다.
	 * 받은 초대(INVITED)뿐 아니라 **내가 owner로 만든 INVITING 팀**도 함께 정리한다. ("한 팀만" 보장 — 수락 = 나머지 진행 중 초대 자동 정리)
	 * 각 팀은 [Team.withdrawInvitation](거절·취소)과 동일하게 DEACTIVATED + 소프트 삭제되고, 상대에게 알람 이벤트를 발행한다.
	 */
	private fun deactivateOtherInvitations(userId: Long, acceptedTeamId: Long) {
		val now: LocalDateTime = timeGenerator.now()
		getTeamPort.findInvitingTeamsByMember(userId)
			.filter { other: Team -> other.id != acceptedTeamId }
			.forEach { other: Team ->
				saveTeamPort.save(other.withdrawInvitation(userId, now))
				publishWithdrawn(other, withdrawnBy = userId)
			}
	}

	/**
	 * 자동 정리된 팀의 상대에게 보낼 이벤트를 발행한다. 역할은 철회 직전(INVITING) 팀의 구성원 상태에서 읽는다.
	 * [userId]가 초대받은 사람이면 거절([TeamInvitationDeclined], 초대자에게), 초대자(owner)면 취소([TeamInvitationCanceled], 초대받은 사람에게).
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
