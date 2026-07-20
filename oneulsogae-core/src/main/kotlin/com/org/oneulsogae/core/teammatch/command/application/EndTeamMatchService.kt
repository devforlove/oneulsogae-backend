package com.org.oneulsogae.core.teammatch.command.application

import com.org.oneulsogae.common.chat.ChatRoomMatchType
import com.org.oneulsogae.core.chat.command.application.port.`in`.DeactivateChatRoomMemberUseCase
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.common.event.DomainEventPublisher
import com.org.oneulsogae.core.common.lock.DistributedLock
import com.org.oneulsogae.core.common.lock.LockKeyConstraints
import com.org.oneulsogae.core.common.time.TimeGenerator
import com.org.oneulsogae.core.teammatch.TeamMatchErrorCode
import com.org.oneulsogae.core.teammatch.command.application.port.`in`.EndTeamMatchUseCase
import com.org.oneulsogae.core.teammatch.command.application.port.out.GetTeamMatchPort
import com.org.oneulsogae.core.teammatch.command.application.port.out.GetTeamPort
import com.org.oneulsogae.core.teammatch.command.application.port.out.SaveTeamMatchPort
import com.org.oneulsogae.core.teammatch.command.domain.Team
import com.org.oneulsogae.core.teammatch.command.domain.TeamMatch
import com.org.oneulsogae.core.teammatch.command.domain.Teams
import com.org.oneulsogae.core.teammatch.command.domain.event.TeamMatchEnded
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [EndTeamMatchUseCase] 구현. 성사된 팀 매칭을 한 팀이 종료한다. (1:1 [EndMatchService] 미러)
 * 팀 매칭·참가 두 팀을 로드해 행위자가 속한 ACTIVE 팀을 식별하고, 종료 가능 상태를 검증한 뒤 처리한다:
 * 내 팀 참가([com.org.oneulsogae.core.teammatch.command.domain.MatchedTeam])만 비활성·소프트 삭제하고(상대도 모두 나갔으면 헤더까지 CLOSED·소프트 삭제),
 * 우리 팀원 전원을 채팅방에서 비활성화하며(남는 상대 팀엔 나감 안내), 방에 남는 상대 팀이 있으면 종료 알림을 발행한다.
 *
 * 상태 변경·채팅 처리는 같은 트랜잭션이라 한 단계라도 실패하면 함께 롤백된다. 알림만 커밋 후 best-effort([TeamMatchEventHandler])다.
 * 다른 도메인(chat)은 자기 out-port가 아니라 in-port로 참조한다.
 * 팀 매칭별 분산 락([DistributedLock], "TEAM_MATCH_INTEREST::{teamMatchId}")으로 신청/수락과의 경합을 막는다. (waitTime=0, 겹치면 즉시 409)
 */
@Service
class EndTeamMatchService(
	private val getTeamMatchPort: GetTeamMatchPort,
	private val saveTeamMatchPort: SaveTeamMatchPort,
	private val getTeamPort: GetTeamPort,
	private val deactivateChatRoomMemberUseCase: DeactivateChatRoomMemberUseCase,
	private val domainEventPublisher: DomainEventPublisher,
	private val timeGenerator: TimeGenerator,
) : EndTeamMatchUseCase {

	@DistributedLock(prefix = LockKeyConstraints.TEAM_MATCH_INTEREST, keys = ["#teamMatchId"], waitTime = 0)
	@Transactional
	override fun endTeamMatch(userId: Long, teamMatchId: Long) {
		val teamMatch: TeamMatch = getTeamMatchPort.findById(teamMatchId)
			?: throw BusinessException(TeamMatchErrorCode.TEAM_MATCH_NOT_FOUND)

		// 참가 두 팀을 로드해 행위자가 ACTIVE 구성원으로 속한 팀을 식별한다. (참가 검증 겸함)
		val teams: Teams = Teams(teamMatch.teamIds().mapNotNull { teamId: Long -> getTeamPort.findById(teamId) })
		val actorTeam: Team = teams.findByActiveMember(userId)
			?: throw BusinessException(TeamMatchErrorCode.NOT_TEAM_MATCH_PARTICIPANT)

		teamMatch.validateTerminable(actorTeam.id)

		val now: LocalDateTime = timeGenerator.now()
		// 상대 팀이 이미 나갔는지(= 알릴 상대가 없는 마지막 종료인지)를 leave 전에 판단한다.
		val isLastTeam: Boolean = teamMatch.isLastActiveTeam(actorTeam.id)

		saveTeamMatchPort.save(teamMatch.leave(actorTeam.id, now))

		// 우리 팀원 전원을 채팅방에서 비활성화한다. (남는 상대 팀에 "상대 팀이 매칭을 종료했어요" 안내 — 기존 TEAM 분기 재사용)
		deactivateChatRoomMemberUseCase.deactivate(ChatRoomMatchType.TEAM, teamMatchId, actorTeam.activeMemberIds())

		if (!isLastTeam) {
			domainEventPublisher.publish(
				TeamMatchEnded(
					teamMatchId = teamMatchId,
					fromTeamId = actorTeam.id,
					recipientUserIds = teams.opponentActiveMemberIds(actorTeam.id),
				),
			)
		}
	}
}
