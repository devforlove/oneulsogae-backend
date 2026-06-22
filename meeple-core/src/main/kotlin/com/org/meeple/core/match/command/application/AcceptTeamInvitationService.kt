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
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [AcceptTeamInvitationUseCase] 구현. 초대받은 사용자가 팀 초대를 수락한다.
 * 팀을 조회해 [Team.acceptInvitation]으로 상태를 전이(전원 수락 시 FORMED)한 뒤 저장한다.
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
		domainEventPublisher.publish(
			TeamInvitationAccepted(accepted.id, inviterUserId = accepted.inviterId(), invitedUserId = userId),
		)
		return accepted
	}

	/**
	 * [userId]가 받은 초대 중 방금 수락한 [acceptedTeamId] 외의 INVITING 팀들을 모두 비활성화한다.
	 * 각 팀은 [Team.withdrawInvitation](거절)과 동일하게 DEACTIVATED + 소프트 삭제된다.
	 */
	private fun deactivateOtherInvitations(userId: Long, acceptedTeamId: Long) {
		val now: LocalDateTime = timeGenerator.now()
		getTeamPort.findInvitedTeams(userId)
			.filter { other: Team -> other.id != acceptedTeamId }
			.forEach { other: Team -> saveTeamPort.save(other.withdrawInvitation(userId, now)) }
	}
}
