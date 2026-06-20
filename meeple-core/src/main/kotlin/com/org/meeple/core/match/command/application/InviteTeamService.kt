package com.org.meeple.core.match.command.application

import com.org.meeple.common.user.Gender
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.match.MatchErrorCode
import com.org.meeple.core.match.TeamErrorCode
import com.org.meeple.core.match.command.application.port.`in`.InviteTeamUseCase
import com.org.meeple.core.match.command.application.port.`in`.command.InviteTeamCommand
import com.org.meeple.core.match.command.application.port.out.GetMatchUserPort
import com.org.meeple.core.match.command.application.port.out.GetTeamPort
import com.org.meeple.core.match.command.application.port.out.SaveTeamPort
import com.org.meeple.core.match.command.domain.Team
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [InviteTeamUseCase] 구현. 초대자가 다른 사용자를 초대해 팀을 결성한다. (초대 대상은 초대중(INVITED)으로 담기고, 수락해야 합류)
 * 구성원의 성별은 팀 구성(성별 균형)에 필요하므로, match 도메인 소유 읽기 모델([GetMatchUserPort], match_user)에서 읽어 채운다.
 * (자기 도메인 내부 영속성 접근은 자기 out-port를 쓴다. match_user 행이 없으면 매칭 불가이므로 PROFILE_INCOMPLETE를 던진다)
 * 이름·소개·자기 초대·동일 성별 검증은 [Team.invite] 도메인 팩토리가 담당한다.
 * 한 사용자는 활성 팀(INVITING/FORMED)에 하나만 속할 수 있다. 오케스트레이션 검증은 이 서비스가 담당한다.
 */
@Service
class InviteTeamService(
	private val getMatchUserPort: GetMatchUserPort,
	private val getTeamPort: GetTeamPort,
	private val saveTeamPort: SaveTeamPort,
) : InviteTeamUseCase {

	@Transactional
	override fun invite(ownerId: Long, command: InviteTeamCommand): Team {
		// 한 사용자는 활성 팀(INVITING/FORMED)에 하나만 속할 수 있다. owner·초대대상 모두 검증.
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
		)
		return saveTeamPort.save(team)
	}

	// 이미 활성 팀 구성원이면 ALREADY_IN_TEAM.
	private fun validateNotInActiveTeam(userId: Long) {
		if (getTeamPort.existsActiveTeamMember(userId)) {
			throw BusinessException(TeamErrorCode.ALREADY_IN_TEAM)
		}
	}

	// 매칭 읽기 모델(match_user)에서 성별을 읽는다. 행이 없으면 매칭 가능 상태가 아니므로 예외.
	private fun genderOf(userId: Long): Gender =
		getMatchUserPort.findByUserId(userId)?.gender
			?: throw BusinessException(MatchErrorCode.PROFILE_INCOMPLETE)
}
