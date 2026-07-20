package com.org.oneulsogae.core.teammatch.command.application

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.matchuser.MatchUserErrorCode
import com.org.oneulsogae.core.teammatch.TeamErrorCode
import com.org.oneulsogae.core.teammatch.command.application.port.`in`.InviteTeamUseCase
import com.org.oneulsogae.core.teammatch.command.application.port.`in`.command.InviteTeamCommand
import com.org.oneulsogae.core.common.event.DomainEventPublisher
import com.org.oneulsogae.core.common.lock.DistributedLock
import com.org.oneulsogae.core.common.lock.LockKeyConstraints
import com.org.oneulsogae.core.matchuser.command.application.port.`in`.GetMatchUserUseCase
import com.org.oneulsogae.core.teammatch.command.application.port.out.GetTeamPort
import com.org.oneulsogae.core.teammatch.command.application.port.out.SaveTeamPort
import com.org.oneulsogae.core.teammatch.command.domain.Team
import com.org.oneulsogae.core.teammatch.command.domain.event.TeamInvitationSent
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [InviteTeamUseCase] 구현. 초대자가 다른 사용자를 초대해 팀을 결성한다. (초대 대상은 초대중(INVITED)으로 담기고, 수락해야 합류)
 * 구성원의 성별은 팀 구성(성별 균형)에 필요하므로, matchuser 도메인 in-port([GetMatchUserUseCase], match_user 읽기 모델)에서 읽어 채운다.
 * (match_user 행이 없으면 매칭 불가이므로 PROFILE_INCOMPLETE를 던진다)
 * 이름·소개·자기 초대·동일 성별 검증은 [Team.invite] 도메인 팩토리가 담당한다.
 * 한 사용자는 활성 팀(INVITING/ACTIVE)에 하나만 속할 수 있다. 오케스트레이션 검증은 이 서비스가 담당한다.
 */
@Service
class InviteTeamService(
	private val getMatchUserUseCase: GetMatchUserUseCase,
	private val getTeamPort: GetTeamPort,
	private val saveTeamPort: SaveTeamPort,
	private val domainEventPublisher: DomainEventPublisher,
) : InviteTeamUseCase {

	// ownerId로 잠가 같은 사용자의 동시 초대(더블탭 등)를 직렬화한다. 검사→생성 사이 경합으로 owner가 두 활성 팀을 갖는 것을 막는다. (waitTime=0)
	@DistributedLock(prefix = LockKeyConstraints.TEAM_MEMBERSHIP, keys = ["#ownerId"], waitTime = 0)
	@Transactional
	override fun invite(ownerId: Long, command: InviteTeamCommand): Team {
		// 한 사용자는 활성 팀(INVITING/ACTIVE)에 하나만 속할 수 있다. owner·초대대상 모두 검증.
		validateNotInActiveTeam(ownerId)
		validateNotInActiveTeam(command.invitedUserId)

		val ownerGender: Gender = genderOf(ownerId)
		val invitedGender: Gender = genderOf(command.invitedUserId)

		val team: Team = Team.invite(
			ownerId = ownerId,
			ownerGender = ownerGender,
			invitedUserId = command.invitedUserId,
			invitedGender = invitedGender,
			name = command.name,
			introduction = command.introduction,
			regionId = command.regionId,
		)
		val savedTeam: Team = saveTeamPort.save(team)
		// 초대받은 사용자에게 보낼 후속 알람은 커밋 이후 핸들러가 처리한다.
		domainEventPublisher.publish(TeamInvitationSent.from(savedTeam, inviterUserId = ownerId, invitedUserId = command.invitedUserId))
		return savedTeam
	}

	// 이미 활성 팀 구성원이면 ALREADY_IN_TEAM.
	private fun validateNotInActiveTeam(userId: Long) {
		if (getTeamPort.existsActiveTeamMember(userId)) {
			throw BusinessException(TeamErrorCode.ALREADY_IN_TEAM)
		}
	}

	// 매칭 읽기 모델(match_user)에서 성별을 읽는다. 행이 없으면 매칭 가능 상태가 아니므로 예외.
	private fun genderOf(userId: Long): Gender =
		getMatchUserUseCase.findByUserId(userId)?.gender
			?: throw BusinessException(MatchUserErrorCode.PROFILE_INCOMPLETE)
}
