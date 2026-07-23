package com.org.oneulsogae.core.lounge.command.application

import com.org.oneulsogae.common.coin.CoinGetType
import com.org.oneulsogae.core.coin.command.application.port.`in`.AcquireCoinUseCase
import com.org.oneulsogae.core.coin.command.application.port.`in`.command.AcquireCoinCommand
import com.org.oneulsogae.core.common.lock.DistributedLock
import com.org.oneulsogae.core.common.lock.LockKeyConstraints
import com.org.oneulsogae.core.common.time.TimeGenerator
import com.org.oneulsogae.core.lounge.command.application.port.`in`.ExpireLoungeChatRequestUseCase
import com.org.oneulsogae.core.lounge.command.application.port.out.DeleteLoungeChatRequestPort
import com.org.oneulsogae.core.lounge.command.application.port.out.GetLoungeChatRequestPort
import com.org.oneulsogae.core.lounge.command.domain.LoungeChatRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * [ExpireLoungeChatRequestUseCase] 구현. 만료된 대화 신청 1건을 한 트랜잭션으로 정리한다.
 * soft-delete와 신청자 코인 절반 환불([LoungeChatRequest.expiryRefundAmount])이 같은 트랜잭션에서 커밋되므로,
 * 다음 실행 시 같은 신청이 다시 조회·환불되지 않는다. 코인 적립([AcquireCoinUseCase])은 코인 도메인 in-port로만 호출한다.
 *
 * 수락([AcceptLoungeChatService])과 같은 "LOUNGE_CHAT_ACCEPT::{requestId}" 락을 공유한다.
 * 락이 없으면 만료 id 조회 시점과 삭제 사이에 수락이 끼어들어, 채팅방·수락 차감은 살아있는데
 * 신청만 삭제되고 환불까지 지급되는 lost update가 가능하다. 락을 잡고 그 안에서 [LoungeChatRequest.isExpired]를
 * 재검증해 막는다. (수락되어 ACCEPTED가 됐으면 만료가 아니므로 그대로 둔다)
 */
@Service
class ExpireLoungeChatRequestService(
	private val getLoungeChatRequestPort: GetLoungeChatRequestPort,
	private val deleteLoungeChatRequestPort: DeleteLoungeChatRequestPort,
	private val acquireCoinUseCase: AcquireCoinUseCase,
	private val timeGenerator: TimeGenerator,
) : ExpireLoungeChatRequestUseCase {

	@DistributedLock(prefix = LockKeyConstraints.LOUNGE_CHAT_ACCEPT, keys = ["#requestId"])
	@Transactional
	override fun expire(requestId: Long) {
		val request: LoungeChatRequest = getLoungeChatRequestPort.findById(requestId) ?: return
		val now: LocalDateTime = timeGenerator.now()
		// 만료 id 조회 후(락 안에서 최신 상태로) 수락된 신청을 삭제·환불하지 않도록 재검증
		if (!request.isExpired(now)) return
		deleteLoungeChatRequestPort.delete(requestId, now)
		acquireCoinUseCase.acquire(
			request.requesterUserId,
			AcquireCoinCommand(amount = request.expiryRefundAmount(), coinType = CoinGetType.REFUND),
		)
	}
}
