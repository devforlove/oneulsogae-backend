package com.org.oneulsogae.core.solomatch.command.application

import com.org.oneulsogae.common.chat.ChatRoomMatchType
import com.org.oneulsogae.common.coin.CoinUsageType
import com.org.oneulsogae.common.match.MatchStatus
import com.org.oneulsogae.core.chat.command.application.port.`in`.SaveChatRoomUseCase
import com.org.oneulsogae.core.chat.command.application.port.`in`.command.SaveChatRoomCommand
import com.org.oneulsogae.core.chat.command.application.port.`in`.command.SaveChatRoomParticipant
import com.org.oneulsogae.core.coin.command.application.port.`in`.SpendCoinUseCase
import com.org.oneulsogae.core.coin.command.application.port.`in`.command.SpendCoinCommand
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.common.event.DomainEventPublisher
import com.org.oneulsogae.core.common.lock.DistributedLock
import com.org.oneulsogae.core.common.lock.LockKeyConstraints
import com.org.oneulsogae.core.solomatch.MatchErrorCode
import com.org.oneulsogae.core.solomatch.command.application.port.`in`.SendInterestUseCase
import com.org.oneulsogae.core.solomatch.command.application.port.out.GetMatchPort
import com.org.oneulsogae.core.solomatch.command.application.port.out.SaveMatchPort
import com.org.oneulsogae.core.solomatch.command.domain.Match
import com.org.oneulsogae.core.solomatch.command.domain.event.InterestSent
import com.org.oneulsogae.core.solomatch.command.domain.event.MatchAccepted
import com.org.oneulsogae.core.user.query.service.port.`in`.CheckCompanyVerifiedUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [SendInterestUseCase] 구현. 신청과 수락을 하나로 처리한다.
 * 참가자 여부·미종료를 검증한 뒤 관심을 반영하고, **결과 매칭 상태**로 후속 처리를 나눈다.
 * (상대가 이미 관심을 보냈으면 respond 결과가 MATCHED가 되므로, 별도 if 없이 상태로 신청/수락을 구분한다)
 * - 성사(MATCHED): 수락 비용 차감 + 채팅방 생성(동기) + 성사 알람 위임. ([completeMatch])
 * - 미성사(PARTIALLY_ACCEPTED): 신청 비용 차감 + 관심 받음 알람 위임. ([recordInterest])
 *
 * 신청/수락 비용은 행위자(참가자) 성별 기준으로 서버가 산출한다. (클라이언트가 금액을 정하지 않음)
 * 코인 차감·상태 변경·채팅방 생성은 같은 트랜잭션이라 한 단계라도 실패하면 함께 롤백된다. 알람만 커밋 후 best-effort([MatchEventHandler])다.
 * 다른 도메인(coin/chat)은 자기 out-port가 아니라 in-port로 참조한다.
 * 회사 인증 여부는 user 도메인 in-port([CheckCompanyVerifiedUseCase])로 검증한다. (미인증이면 코인 차감 전에 403으로 막는다)
 *
 * 매칭별 분산 락([DistributedLock])으로 보호한다. 락 키는 "MATCH_INTEREST::{matchId}" 형태로 매칭마다 독립적으로 잠긴다.
 * 경합 대상이 두 참가자가 공유하는 "매칭"이므로 userId가 아니라 matchId로 잠가야 한다. (사용자별로 잠그면 남/녀가
 * 서로 다른 키라 동시에 통과해 [Match.respond]에서 lost update가 나고, 둘 다 수락해도 MATCHED가 안 될 수 있다)
 * waitTime=0이라 같은 매칭에 동시 요청이 겹치면 한쪽은 즉시 실패(409)한다. (멱등성 가드가 없어 더블클릭 이중 과금을 막는 fail-fast)
 */
@Service
class SendInterestService(
	private val getMatchPort: GetMatchPort,
	private val saveMatchPort: SaveMatchPort,
	private val spendCoinUseCase: SpendCoinUseCase,
	private val saveChatRoomUseCase: SaveChatRoomUseCase,
	private val domainEventPublisher: DomainEventPublisher,
	private val checkCompanyVerifiedUseCase: CheckCompanyVerifiedUseCase,
) : SendInterestUseCase {

	@DistributedLock(prefix = LockKeyConstraints.MATCH_INTEREST, keys = ["#matchId"], waitTime = 0)
	@Transactional
	override fun sendInterest(userId: Long, matchId: Long): Match {
		// 회사 인증을 마친 사용자만 소개 기능을 쓸 수 있다. 코인 차감 전에 막아 미인증 요청에 과금이 생기지 않게 한다.
		checkCompanyVerifiedUseCase.validateCompanyVerified(userId)

		// 대상 매칭을 조회한다. 없으면 예외.
		val match: Match = getMatchPort.findById(matchId)
			?: throw BusinessException(MatchErrorCode.MATCH_NOT_FOUND)
		match.validateRespondable(userId)

		// 관심을 반영해 저장한다. 결과 상태로 신청/수락 후속 처리를 분기한다.
		val updated: Match = saveMatchPort.save(match.respond(userId))
		return when (updated.status) {
			MatchStatus.MATCHED -> completeMatch(userId, updated)
			MatchStatus.PARTIALLY_ACCEPTED -> recordInterest(userId, updated)
			// respond는 참가자의 수락을 반영하므로 결과는 항상 MATCHED/PARTIALLY_ACCEPTED다. (그 외는 도달 불가)
			else -> error("관심 보내기 결과 상태가 올바르지 않습니다: ${updated.status}")
		}
	}

	/** 수락으로 성사된 경우: 수락 비용 차감 + 채팅방 생성(동기) + 성사 알람 위임. */
	private fun completeMatch(userId: Long, match: Match): Match {
		spend(userId, amount = match.datingAcceptAmountFor(userId), usageType = CoinUsageType.DATING_ACCEPT)
		// 채팅방 생성은 성사의 필수 산출물이라 같은 트랜잭션에서 동기로 처리한다. (실패 시 함께 롤백)
		saveChatRoomUseCase.save(
			SaveChatRoomCommand(
				matchType = ChatRoomMatchType.SOLO,
				matchId = match.id,
				participants = match.participantUserIds().map { memberId: Long -> SaveChatRoomParticipant(userId = memberId, teamId = null) },
			),
		)
		// 성사 알람만 이벤트로 위임한다. (수락자는 이번에 관심을 보낸 본인)
		domainEventPublisher.publish(MatchAccepted.from(match, acceptedByUserId = userId))
		return match
	}

	/** 아직 미성사(신청)인 경우: 신청 비용 차감 + 상대에게 관심 받음 알람 위임. */
	private fun recordInterest(userId: Long, match: Match): Match {
		spend(userId, amount = match.datingInitAmountFor(userId), usageType = CoinUsageType.DATING_INIT)
		domainEventPublisher.publish(InterestSent.from(match, userId))
		return match
	}

	/** 매칭에 저장된 금액·유형으로 코인을 차감한다. (같은 트랜잭션이라 이후 실패 시 함께 롤백) */
	private fun spend(userId: Long, amount: Int, usageType: CoinUsageType) {
		spendCoinUseCase.spend(userId, SpendCoinCommand(amount = amount, coinUsageType = usageType))
	}
}
