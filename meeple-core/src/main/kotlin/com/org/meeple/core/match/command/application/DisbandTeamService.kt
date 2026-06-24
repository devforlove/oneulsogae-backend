package com.org.meeple.core.match.command.application

import com.org.meeple.common.chat.ChatRoomMatchType
import com.org.meeple.core.chat.command.application.port.`in`.DeactivateChatRoomMemberUseCase
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.common.event.DomainEventPublisher
import com.org.meeple.core.common.lock.DistributedLock
import com.org.meeple.core.common.lock.LockKeyConstraints
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.match.TeamErrorCode
import com.org.meeple.core.match.command.application.port.`in`.DisbandTeamUseCase
import com.org.meeple.core.match.command.application.port.out.GetTeamMatchPort
import com.org.meeple.core.match.command.application.port.out.GetTeamPort
import com.org.meeple.core.match.command.application.port.out.SaveTeamMatchPort
import com.org.meeple.core.match.command.application.port.out.SaveTeamPort
import com.org.meeple.core.match.command.domain.Team
import com.org.meeple.core.match.command.domain.TeamMatch
import com.org.meeple.core.match.command.domain.event.TeamDisbanded
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [DisbandTeamUseCase] 구현. ACTIVE 팀을 구성원이 해체한다.
 * 팀을 [Team.disband]로 비활성화한 뒤, 그 팀이 참가한 진행 중(미종료) 팀 매칭을 상태별로 정리한다.
 * - 미성사(PROPOSED/PARTIALLY_ACCEPTED): [TeamMatch.close]로 매칭 종료(CLOSED + 참가 팀 DEACTIVE)
 * - 성사(MATCHED): 매칭은 유지하고, 나간 팀원의 채팅 참가만 비활성화([DeactivateChatRoomMemberUseCase])해 채팅방 입장을 막는다
 * 해체를 실행한 구성원([userId])을 제외한 같은 팀의 남은 구성원을 알림 수신자로 모아, 커밋 이후 알림이 가도록 [TeamDisbanded]를 발행한다.
 * 모두 같은 트랜잭션에서 처리해 함께 성공/롤백된다. teamId 분산 락으로 동시 상태 변경과 직렬화한다.
 * 시각은 [TimeGenerator]로 얻어 도메인에 주입한다. (LocalDateTime.now() 직접 호출 금지)
 */
@Service
class DisbandTeamService(
	private val getTeamPort: GetTeamPort,
	private val saveTeamPort: SaveTeamPort,
	private val getTeamMatchPort: GetTeamMatchPort,
	private val saveTeamMatchPort: SaveTeamMatchPort,
	private val deactivateChatRoomMemberUseCase: DeactivateChatRoomMemberUseCase,
	private val domainEventPublisher: DomainEventPublisher,
	private val timeGenerator: TimeGenerator,
) : DisbandTeamUseCase {

	// 락은 해체 대상 팀에만 건다. 팀 매칭 성사(accept) 흐름 구현 시, 상대 팀 매칭/채팅 동시 변경과의 경합을 막도록 락 범위를 재검토한다.
	@DistributedLock(prefix = LockKeyConstraints.TEAM_LIFECYCLE, keys = ["#teamId"], waitTime = 0)
	@Transactional
	override fun disband(userId: Long, teamId: Long): Team {
		val team: Team = getTeamPort.findById(teamId)
			?: throw BusinessException(TeamErrorCode.TEAM_NOT_FOUND)
		val memberIds: List<Long> = team.activeMemberIds()
		val disbandedTeam: Team = saveTeamPort.save(team.disband(userId, timeGenerator.now()))

		teardownMatches(teamId, memberIds)

		// 해체 실행자를 제외한 남은 팀 구성원에게 팀 해체 알림.
		val recipientUserIds: List<Long> = memberIds.filter { memberId: Long -> memberId != userId }
		if (recipientUserIds.isNotEmpty()) {
			domainEventPublisher.publish(TeamDisbanded(teamId, recipientUserIds))
		}
		return disbandedTeam
	}

	// 진행 중(미종료) 매칭을 상태별로 정리한다. (미성사는 종료, 성사는 유지하고 나간 팀원의 채팅 참가만 비활성화)
	private fun teardownMatches(teamId: Long, leavingMemberIds: List<Long>) {
		getTeamMatchPort.findActiveByTeamId(teamId).forEach { teamMatch: TeamMatch ->
			if (teamMatch.isMatched()) {
				deactivateChatRoomMemberUseCase.deactivate(ChatRoomMatchType.TEAM, teamMatch.id, leavingMemberIds)
			} else {
				saveTeamMatchPort.save(teamMatch.close())
			}
		}
	}
}
