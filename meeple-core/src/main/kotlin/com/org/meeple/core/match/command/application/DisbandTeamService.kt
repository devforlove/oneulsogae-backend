package com.org.meeple.core.match.command.application

import com.org.meeple.common.chat.ChatRoomMatchType
import com.org.meeple.common.match.TeamStatus
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
import com.org.meeple.core.match.command.domain.event.TeamMatchEnded
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [DisbandTeamUseCase] 구현. 결성(ACTIVE)/해체중(DISBANDED) 팀에서 구성원 한 명이 떠난다(해체). 두 단계로 갈린다([Team.disband]):
 * - 남은 팀원이 있으면(1단계) 팀은 [TeamStatus.DISBANDED]가 되고, **매칭/matched_team은 그대로 유지**한 채 떠나는 본인만 채팅방에서 비활성화한다.
 *   같은 팀의 남은 구성원에게 [TeamDisbanded] 알림을 발행한다.
 * - 마지막 구성원이 떠나면(2단계) 팀은 [TeamStatus.DEACTIVATED]가 되고, 참가한 진행 중 팀 매칭마다 우리 팀이 [TeamMatch.leave]로 빠진다.
 *   (상대 팀이 남아 있으면 헤더는 유지, 상대도 나갔으면 헤더까지 CLOSED) 떠나는 본인을 채팅방에서 비활성화하며 방에 남는 상대 팀에 종료 안내를 남기고,
 *   상대 팀 활성 구성원에게 [TeamMatchEnded] 알림을 발행한다.
 * 모두 같은 트랜잭션에서 처리해 함께 성공/롤백된다(알림만 커밋 이후 best-effort). teamId 분산 락으로 동시 상태 변경과 직렬화한다.
 * (팀 매칭별 락은 잡지 않지만, 팀 매칭은 낙관적 락([TeamMatch.version])으로 보호되어 관심/수락과 경합하면 한쪽이 충돌(409)로 롤백된다)
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

	// 락은 해체 대상 팀에만 건다. 팀 매칭과의 경합은 팀 매칭 헤더의 낙관적 락([TeamMatch.version])이 감지해 한쪽이 충돌로 롤백된다.
	@DistributedLock(prefix = LockKeyConstraints.TEAM_LIFECYCLE, keys = ["#teamId"], waitTime = 0)
	@Transactional
	override fun disband(userId: Long, teamId: Long): Team {
		val team: Team = getTeamPort.findById(teamId)
			?: throw BusinessException(TeamErrorCode.TEAM_NOT_FOUND)
		val now: LocalDateTime = timeGenerator.now()
		val disbandedTeam: Team = saveTeamPort.save(team.disband(userId, now))
		val activeMatches: List<TeamMatch> = getTeamMatchPort.findActiveByTeamId(teamId)

		if (disbandedTeam.status == TeamStatus.DEACTIVATED) {
			// 마지막 구성원 → 매칭에서 우리 팀이 빠지고, 방에 남는 상대 팀에 종료를 알린다.
			endMatches(teamId, userId, activeMatches, now)
		} else {
			// 1단계 → 떠나는 본인만 조용히(안내 메세지 없이) 채팅방에서 비활성화하고, 남은 팀원에게 알린다.
			activeMatches.forEach { teamMatch: TeamMatch ->
				deactivateChatRoomMemberUseCase.deactivate(ChatRoomMatchType.TEAM, teamMatch.id, listOf(userId), notifyRemaining = false)
			}
			notifyRemainingTeammates(disbandedTeam, userId)
		}
		return disbandedTeam
	}

	// 2단계: 참가 중인 팀 매칭마다 우리 팀이 빠지고(leave), 떠나는 본인을 채팅방에서 비활성화하며 남는 상대 팀에 종료 안내·알림을 보낸다.
	private fun endMatches(teamId: Long, userId: Long, activeMatches: List<TeamMatch>, now: LocalDateTime) {
		activeMatches.forEach { teamMatch: TeamMatch ->
			// 상대 팀이 이미 나갔는지(= 알릴 상대가 없는 마지막 종료인지)를 leave 전에 판단한다.
			val isLastTeam: Boolean = teamMatch.isLastActiveTeam(teamId)
			saveTeamMatchPort.save(teamMatch.leave(teamId, now))
			// 떠나는 본인만 비활성화한다. 방에 남는 상대 팀에 "상대 팀이 매칭을 종료했어요" 안내를 남긴다. (상대도 없으면 방이 닫히고 안내는 생략)
			deactivateChatRoomMemberUseCase.deactivate(ChatRoomMatchType.TEAM, teamMatch.id, listOf(userId), notifyRemaining = true)

			if (!isLastTeam) {
				val opponentTeamId: Long = teamMatch.teamIds().first { id: Long -> id != teamId }
				val recipientUserIds: List<Long> = getTeamPort.findById(opponentTeamId)?.activeMemberIds().orEmpty()
				if (recipientUserIds.isNotEmpty()) {
					domainEventPublisher.publish(
						TeamMatchEnded(teamMatchId = teamMatch.id, fromTeamId = teamId, recipientUserIds = recipientUserIds),
					)
				}
			}
		}
	}

	// 1단계: 떠난 본인을 제외한 같은 팀의 남은 활성 구성원에게 팀 해체 알림을 발행한다. (disband 결과 모델에서 본인은 이미 DEACTIVE)
	private fun notifyRemainingTeammates(disbandedTeam: Team, userId: Long) {
		val recipientUserIds: List<Long> = disbandedTeam.activeMemberIds()
		if (recipientUserIds.isNotEmpty()) {
			domainEventPublisher.publish(TeamDisbanded(disbandedByUserId = userId, recipientUserIds = recipientUserIds))
		}
	}
}
