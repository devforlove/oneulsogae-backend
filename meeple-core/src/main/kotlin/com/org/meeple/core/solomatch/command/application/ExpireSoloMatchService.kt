package com.org.meeple.core.solomatch.command.application

import com.org.meeple.common.coin.CoinGetType
import com.org.meeple.core.coin.command.application.port.`in`.AcquireCoinUseCase
import com.org.meeple.core.coin.command.application.port.`in`.command.AcquireCoinCommand
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.solomatch.command.application.port.`in`.ExpireSoloMatchUseCase
import com.org.meeple.core.solomatch.command.application.port.out.GetMatchPort
import com.org.meeple.core.solomatch.command.application.port.out.SaveMatchPort
import com.org.meeple.core.solomatch.command.domain.Match
import com.org.meeple.core.solomatch.command.domain.MatchRefund
import com.org.meeple.core.popup.command.application.port.`in`.CreateRefundPopupUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [ExpireSoloMatchUseCase] 구현. 만료된(미성사) 솔로 매칭 1건을 한 트랜잭션으로 정리한다.
 * soft-delete와 코인 환불·팝업이 같은 트랜잭션에서 커밋되므로, 다음 실행 시 같은 매칭이 다시 조회·환불되지 않는다.
 * 코인 적립([AcquireCoinUseCase])·팝업 생성([CreateRefundPopupUseCase])은 다른 도메인 in-port로만 호출한다.
 */
@Service
class ExpireSoloMatchService(
	private val getMatchPort: GetMatchPort,
	private val saveMatchPort: SaveMatchPort,
	private val acquireCoinUseCase: AcquireCoinUseCase,
	private val createRefundPopupUseCase: CreateRefundPopupUseCase,
	private val timeGenerator: TimeGenerator,
) : ExpireSoloMatchUseCase {

	@Transactional
	override fun expireSoloMatch(matchId: Long) {
		val match: Match = getMatchPort.findById(matchId) ?: return
		// 만료 id 조회 후 사용자 신청으로 성사(MATCHED)된 매칭을 환불·삭제하지 않도록 재검증
		if (match.isClosed) return
		val now: LocalDateTime = timeGenerator.now()
		val refunds: List<MatchRefund> = match.failureRefunds()
		saveMatchPort.save(match.delete(now))
		refunds.forEach { refund: MatchRefund ->
			acquireCoinUseCase.acquire(refund.userId, AcquireCoinCommand(amount = refund.amount, coinType = CoinGetType.REFUND))
			createRefundPopupUseCase.createMatchFailedRefund(refund.userId, refund.amount)
		}
	}
}
