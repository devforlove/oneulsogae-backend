package com.org.meeple.core.match.command.application

import com.org.meeple.common.chat.ChatRoomMatchType
import com.org.meeple.common.coin.CoinUsageType
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.core.chat.command.application.port.`in`.SaveChatRoomUseCase
import com.org.meeple.core.chat.command.application.port.`in`.command.SaveChatRoomCommand
import com.org.meeple.core.chat.command.application.port.`in`.command.SaveChatRoomParticipant
import com.org.meeple.core.coin.command.application.port.`in`.SpendCoinUseCase
import com.org.meeple.core.coin.command.application.port.`in`.command.SpendCoinCommand
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.common.event.DomainEventPublisher
import com.org.meeple.core.common.lock.DistributedLock
import com.org.meeple.core.common.lock.LockKeyConstraints
import com.org.meeple.core.match.TeamMatchErrorCode
import com.org.meeple.core.match.command.application.port.`in`.SendTeamInterestUseCase
import com.org.meeple.core.match.command.application.port.out.GetTeamMatchPort
import com.org.meeple.core.match.command.application.port.out.GetTeamPort
import com.org.meeple.core.match.command.application.port.out.SaveRecommendedTeamHistoryPort
import com.org.meeple.core.match.command.application.port.out.SaveTeamMatchPort
import com.org.meeple.core.match.command.domain.Team
import com.org.meeple.core.match.command.domain.TeamMatch
import com.org.meeple.core.match.command.domain.TeamMember
import com.org.meeple.core.match.command.domain.Teams
import com.org.meeple.core.match.command.domain.event.TeamMatchAccepted
import com.org.meeple.core.match.command.domain.event.TeamMatchInterestSent
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [SendTeamInterestUseCase] 구현. 팀 매칭의 신청과 수락을 하나로 처리한다. (1:1 [SendInterestService] 미러)
 * 팀 매칭·참가 두 팀을 로드해 행위자가 속한 ACTIVE 팀을 식별하고, 미종료를 검증한 뒤 관심을 반영한다.
 * 결과 매칭 상태로 후속 처리를 나눈다:
 * - 성사(MATCHED): 수락 비용 차감 + 4인 채팅방 생성(동기) + 성사 알림 위임. ([completeMatch])
 * - 미성사(PARTIALLY_ACCEPTED): 신청 비용 차감 + 상대 팀에 관심 받음 알림 위임. ([recordInterest])
 *
 * 코인 차감·상태 변경·채팅방 생성은 같은 트랜잭션이라 한 단계라도 실패하면 함께 롤백된다. 알림만 커밋 후 best-effort([TeamMatchEventHandler])다.
 * 채팅방은 성사의 필수 산출물이라 같은 트랜잭션에서 동기로 만든다. matchId로는 teamMatch.id를 쓴다. (기존 [DisbandTeamService] 컨벤션과 동일)
 * 다른 도메인(coin/chat)은 자기 out-port가 아니라 in-port로 참조한다.
 *
 * 팀 매칭별 분산 락([DistributedLock], "TEAM_MATCH_INTEREST::{teamMatchId}")으로 보호한다. 경합 대상이 두 팀이 공유하는
 * "팀 매칭"이므로 teamMatchId로 잠근다. waitTime=0이라 같은 매칭에 동시 요청이 겹치면 한쪽은 즉시 실패(409)한다.
 */
@Service
class SendTeamInterestService(
	private val getTeamMatchPort: GetTeamMatchPort,
	private val saveTeamMatchPort: SaveTeamMatchPort,
	private val saveRecommendedTeamHistoryPort: SaveRecommendedTeamHistoryPort,
	private val getTeamPort: GetTeamPort,
	private val spendCoinUseCase: SpendCoinUseCase,
	private val saveChatRoomUseCase: SaveChatRoomUseCase,
	private val domainEventPublisher: DomainEventPublisher,
) : SendTeamInterestUseCase {

	@DistributedLock(prefix = LockKeyConstraints.TEAM_MATCH_INTEREST, keys = ["#teamMatchId"], waitTime = 0)
	@Transactional
	override fun sendInterest(userId: Long, teamMatchId: Long): TeamMatch {
		val teamMatch: TeamMatch = getTeamMatchPort.findById(teamMatchId)
			?: throw BusinessException(TeamMatchErrorCode.TEAM_MATCH_NOT_FOUND)

		// 참가 두 팀을 로드해 행위자가 ACTIVE 구성원으로 속한 팀을 식별한다. (참가 검증 겸함)
		val teams: Teams = Teams(teamMatch.matchedTeams.teamIds().mapNotNull { teamId: Long -> getTeamPort.findById(teamId) })
		val actorTeam: Team = teams.findByActiveMember(userId)
			?: throw BusinessException(TeamMatchErrorCode.NOT_TEAM_MATCH_PARTICIPANT)

		teamMatch.validateRespondable(actorTeam.id)

		val updated: TeamMatch = saveTeamMatchPort.save(teamMatch.respond(actorTeam.id))
		return when (updated.status) {
			MatchStatus.MATCHED -> completeMatch(userId, updated, teams)
			MatchStatus.PARTIALLY_ACCEPTED -> recordInterest(userId, updated, actorTeam, teams)
			else -> error("팀 관심 보내기 결과 상태가 올바르지 않습니다: ${updated.status}")
		}
	}

	/** 성사된 경우: 수락 비용 차감 + 4인 채팅방 생성(동기) + 성사 알림 위임(행위자 제외 양 팀 구성원). */
	private fun completeMatch(userId: Long, teamMatch: TeamMatch, teams: Teams): TeamMatch {
		spend(userId, amount = teamMatch.dateAcceptAmount, usageType = CoinUsageType.MEETING_ACCEPT)
		val participants: List<SaveChatRoomParticipant> = teams.activeMembers().map { member: TeamMember ->
			SaveChatRoomParticipant(userId = member.userId, teamId = member.teamId)
		}
		saveChatRoomUseCase.save(
			SaveChatRoomCommand(matchType = ChatRoomMatchType.TEAM, matchId = teamMatch.id, participants = participants),
		)
		// 성사 이력 기록: 양 팀 구성원 ↔ 상대 팀. 추천 배치가 이미 매칭한 상대를 다시 추천하지 않도록 한다. (성사와 같은 트랜잭션)
		saveRecommendedTeamHistoryPort.saveAll(teams.matchHistories())
		// 성사 알림은 양 팀에 각각 발행한다. 각 팀 수신자(행위자 제외)에게 그들의 상대 팀을 fromTeamId로 담아, 알림에서 상대 팀이 보이도록 한다.
		teams.values.forEach { team: Team ->
			val recipientUserIds: List<Long> = team.activeMemberIds().filter { memberId: Long -> memberId != userId }
			if (recipientUserIds.isEmpty()) return@forEach
			domainEventPublisher.publish(
				TeamMatchAccepted(
					teamMatchId = teamMatch.id,
					fromTeamId = teams.opponentTeamId(team.id),
					recipientUserIds = recipientUserIds,
				),
			)
		}
		return teamMatch
	}

	/** 미성사(신청)인 경우: 신청 비용 차감 + 상대 팀 구성원에게 관심 받음 알림 위임. */
	private fun recordInterest(userId: Long, teamMatch: TeamMatch, actorTeam: Team, teams: Teams): TeamMatch {
		spend(userId, amount = teamMatch.dateInitAmount, usageType = CoinUsageType.MEETING_INIT)
		val opponentMemberIds: List<Long> = teams.opponentActiveMemberIds(actorTeam.id)
		domainEventPublisher.publish(
			TeamMatchInterestSent(teamMatchId = teamMatch.id, senderTeamId = actorTeam.id, recipientUserIds = opponentMemberIds),
		)
		return teamMatch
	}

	/** 팀 매칭에 저장된 금액·유형으로 코인을 차감한다. (같은 트랜잭션이라 이후 실패 시 함께 롤백) */
	private fun spend(userId: Long, amount: Int, usageType: CoinUsageType) {
		spendCoinUseCase.spend(userId, SpendCoinCommand(amount = amount, coinUsageType = usageType))
	}
}
