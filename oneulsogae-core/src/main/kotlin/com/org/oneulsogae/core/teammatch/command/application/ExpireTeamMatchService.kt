package com.org.oneulsogae.core.teammatch.command.application

import com.org.oneulsogae.common.coin.CoinGetType
import com.org.oneulsogae.core.coin.command.application.port.`in`.AcquireCoinUseCase
import com.org.oneulsogae.core.coin.command.application.port.`in`.command.AcquireCoinCommand
import com.org.oneulsogae.core.common.time.TimeGenerator
import com.org.oneulsogae.core.teammatch.command.application.port.`in`.ExpireTeamMatchUseCase
import com.org.oneulsogae.core.teammatch.command.application.port.out.GetTeamMatchPort
import com.org.oneulsogae.core.teammatch.command.application.port.out.SaveTeamMatchPort
import com.org.oneulsogae.core.teammatch.command.domain.MatchRefund
import com.org.oneulsogae.core.teammatch.command.domain.TeamMatch
import com.org.oneulsogae.core.popup.command.application.port.`in`.CreateRefundPopupUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [ExpireTeamMatchUseCase] 구현. 만료된(미성사) 팀 매칭 1건을 한 트랜잭션으로 정리한다.
 * soft-delete와 코인 환불·팝업이 같은 트랜잭션에서 커밋되므로, 다음 실행 시 같은 매칭이 다시 조회·환불되지 않는다.
 * 코인 적립([AcquireCoinUseCase])·팝업 생성([CreateRefundPopupUseCase])은 다른 도메인 in-port로만 호출한다.
 */
@Service
class ExpireTeamMatchService(
	private val getTeamMatchPort: GetTeamMatchPort,
	private val saveTeamMatchPort: SaveTeamMatchPort,
	private val acquireCoinUseCase: AcquireCoinUseCase,
	private val createRefundPopupUseCase: CreateRefundPopupUseCase,
	private val timeGenerator: TimeGenerator,
) : ExpireTeamMatchUseCase {

	@Transactional
	override fun expireTeamMatch(teamMatchId: Long) {
		val teamMatch: TeamMatch = getTeamMatchPort.findById(teamMatchId) ?: return
		// 만료 id 조회 후 사용자 신청으로 성사(MATCHED)된 매칭을 환불·삭제하지 않도록 재검증
		if (teamMatch.isClosed) return
		val now: LocalDateTime = timeGenerator.now()
		val refunds: List<MatchRefund> = teamMatch.failureRefunds()
		saveTeamMatchPort.save(teamMatch.delete(now))
		refunds.forEach { refund: MatchRefund ->
			acquireCoinUseCase.acquire(refund.userId, AcquireCoinCommand(amount = refund.amount, coinType = CoinGetType.REFUND))
			createRefundPopupUseCase.createMeetingFailedRefund(refund.userId, refund.amount)
		}
	}
}
