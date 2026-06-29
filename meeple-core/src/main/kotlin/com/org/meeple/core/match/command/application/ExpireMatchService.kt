package com.org.meeple.core.match.command.application

import com.org.meeple.common.coin.CoinGetType
import com.org.meeple.core.coin.command.application.port.`in`.AcquireCoinUseCase
import com.org.meeple.core.coin.command.application.port.`in`.command.AcquireCoinCommand
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.match.command.application.port.`in`.ExpireMatchUseCase
import com.org.meeple.core.match.command.application.port.out.GetMatchPort
import com.org.meeple.core.match.command.application.port.out.GetTeamMatchPort
import com.org.meeple.core.match.command.application.port.out.SaveMatchPort
import com.org.meeple.core.match.command.application.port.out.SaveTeamMatchPort
import com.org.meeple.core.match.command.domain.Match
import com.org.meeple.core.match.command.domain.MatchRefund
import com.org.meeple.core.match.command.domain.TeamMatch
import com.org.meeple.core.popup.command.application.port.`in`.CreateRefundPopupUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [ExpireMatchUseCase] 구현. 만료된(미성사) 매칭 1건을 한 트랜잭션으로 정리한다.
 * soft-delete와 코인 환불·팝업이 같은 트랜잭션에서 커밋되므로, 다음 실행 시 같은 매칭이 다시 조회·환불되지 않는다.
 * 코인 적립([AcquireCoinUseCase])·팝업 생성([CreateRefundPopupUseCase])은 다른 도메인 in-port로만 호출한다.
 */
@Service
class ExpireMatchService(
	private val getMatchPort: GetMatchPort,
	private val saveMatchPort: SaveMatchPort,
	private val getTeamMatchPort: GetTeamMatchPort,
	private val saveTeamMatchPort: SaveTeamMatchPort,
	private val acquireCoinUseCase: AcquireCoinUseCase,
	private val createRefundPopupUseCase: CreateRefundPopupUseCase,
	private val timeGenerator: TimeGenerator,
) : ExpireMatchUseCase {

	@Transactional
	override fun expireSoloMatch(matchId: Long) {
		val match: Match = getMatchPort.findById(matchId) ?: return
		val now: LocalDateTime = timeGenerator.now()
		val refunds: List<MatchRefund> = match.failureRefunds()
		saveMatchPort.save(match.delete(now))
		refunds.forEach { refund: MatchRefund ->
			acquireCoinUseCase.acquire(refund.userId, AcquireCoinCommand(amount = refund.amount, coinType = CoinGetType.REFUND))
			createRefundPopupUseCase.createMatchFailedRefund(refund.userId, refund.amount)
		}
	}

	@Transactional
	override fun expireTeamMatch(teamMatchId: Long) {
		val teamMatch: TeamMatch = getTeamMatchPort.findById(teamMatchId) ?: return
		val now: LocalDateTime = timeGenerator.now()
		val refunds: List<MatchRefund> = teamMatch.failureRefunds()
		saveTeamMatchPort.save(teamMatch.delete(now))
		refunds.forEach { refund: MatchRefund ->
			acquireCoinUseCase.acquire(refund.userId, AcquireCoinCommand(amount = refund.amount, coinType = CoinGetType.REFUND))
			createRefundPopupUseCase.createMeetingFailedRefund(refund.userId, refund.amount)
		}
	}
}
