package com.org.oneulsogae.scheduler.lounge.command.application

import com.org.oneulsogae.scheduler.common.command.application.port.out.TimeGenerator
import com.org.oneulsogae.scheduler.lounge.command.application.port.`in`.RunExpireLoungeChatRequestBatchUseCase
import com.org.oneulsogae.scheduler.lounge.command.application.port.out.ExpireLoungeChatRequestPort
import com.org.oneulsogae.scheduler.lounge.command.application.port.out.GetExpiredLoungeChatRequestPort
import com.org.oneulsogae.scheduler.lounge.command.domain.ExpireLoungeChatRequestBatchResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * [RunExpireLoungeChatRequestBatchUseCase] 구현. 만료 대화 신청 정리 배치.
 * 만료된(미수락) 신청 id를 한 번 적재하고, 건별로 [ExpireLoungeChatRequestPort]에 위임한다(신청당 트랜잭션 1개).
 * 한 건의 실패가 다른 건에 전파되지 않도록 건 단위로 격리하고, 예외만 failed로 집계한다.
 */
@Service
class ExpireLoungeChatRequestBatchService(
	private val getExpiredLoungeChatRequestPort: GetExpiredLoungeChatRequestPort,
	private val expireLoungeChatRequestPort: ExpireLoungeChatRequestPort,
	private val timeGenerator: TimeGenerator,
) : RunExpireLoungeChatRequestBatchUseCase {

	private val log: Logger = LoggerFactory.getLogger(javaClass)

	override fun run(): ExpireLoungeChatRequestBatchResult {
		val now: LocalDateTime = timeGenerator.now()

		var expired = 0
		var failed = 0
		for (requestId: Long in getExpiredLoungeChatRequestPort.findExpiredRequestIds(now)) {
			try {
				expireLoungeChatRequestPort.expire(requestId)
				expired++
			} catch (e: Exception) {
				failed++
				log.warn("만료 대화 신청 정리 실패 requestId={}", requestId, e)
			}
		}

		val result: ExpireLoungeChatRequestBatchResult = ExpireLoungeChatRequestBatchResult(expired = expired, failed = failed)
		log.info("만료 대화 신청 정리 배치 완료: {}", result)
		return result
	}
}
