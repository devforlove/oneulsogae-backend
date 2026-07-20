package com.org.oneulsogae.core.solomatch.command.application

import com.org.oneulsogae.common.coin.CoinGetType
import com.org.oneulsogae.core.coin.command.application.port.`in`.AcquireCoinUseCase
import com.org.oneulsogae.core.coin.command.application.port.`in`.command.AcquireCoinCommand
import com.org.oneulsogae.core.common.lock.DistributedLock
import com.org.oneulsogae.core.common.lock.LockKeyConstraints
import com.org.oneulsogae.core.common.time.TimeGenerator
import com.org.oneulsogae.core.solomatch.command.application.port.`in`.ExpireSoloMatchUseCase
import com.org.oneulsogae.core.solomatch.command.application.port.out.GetMatchPort
import com.org.oneulsogae.core.solomatch.command.application.port.out.SaveMatchPort
import com.org.oneulsogae.core.solomatch.command.domain.Match
import com.org.oneulsogae.core.solomatch.command.domain.MatchRefund
import com.org.oneulsogae.core.popup.command.application.port.`in`.CreateRefundPopupUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [ExpireSoloMatchUseCase] 구현. 만료된(미성사) 솔로 매칭 1건을 한 트랜잭션으로 정리한다.
 * soft-delete와 코인 환불·팝업이 같은 트랜잭션에서 커밋되므로, 다음 실행 시 같은 매칭이 다시 조회·환불되지 않는다.
 * 코인 적립([AcquireCoinUseCase])·팝업 생성([CreateRefundPopupUseCase])은 다른 도메인 in-port로만 호출한다.
 *
 * 신청·수락([SendInterestService])·종료([EndMatchService])와 같은 "MATCH_INTEREST::{matchId}" 락을 공유한다.
 * 락이 없으면 만료 id 조회 시점과 삭제 사이에 사용자 신청으로 성사(MATCHED)가 끼어들어, 채팅방·차감은 살아있는데
 * 매칭만 삭제되고 환불까지 지급되는 lost update가 가능하다. 락을 잡고 그 안에서 [Match.isClosed]를 재검증해 막는다.
 * (waitTime은 기본값. 동시 신청이 진행 중이면 잠깐 대기 후 그 결과를 반영해 처리하고, 그래도 못 잡으면 배치가 다음 회차에 재시도한다)
 */
@Service
class ExpireSoloMatchService(
	private val getMatchPort: GetMatchPort,
	private val saveMatchPort: SaveMatchPort,
	private val acquireCoinUseCase: AcquireCoinUseCase,
	private val createRefundPopupUseCase: CreateRefundPopupUseCase,
	private val timeGenerator: TimeGenerator,
) : ExpireSoloMatchUseCase {

	@DistributedLock(prefix = LockKeyConstraints.MATCH_INTEREST, keys = ["#matchId"])
	@Transactional
	override fun expireSoloMatch(matchId: Long) {
		val match: Match = getMatchPort.findById(matchId) ?: return
		// 만료 id 조회 후(락 안에서 최신 상태로) 사용자 신청으로 성사(MATCHED)된 매칭을 환불·삭제하지 않도록 재검증
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
