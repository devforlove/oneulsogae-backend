package com.org.meeple.scheduler.match.command.application

import com.org.meeple.scheduler.match.command.application.port.`in`.ExpireMatchesUseCase
import com.org.meeple.scheduler.match.command.application.port.out.GetExpiredMatchIdsPort
import com.org.meeple.scheduler.match.command.application.port.out.RemoveMatchPort
import com.org.meeple.scheduler.match.command.application.port.out.TimeGenerator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * [ExpireMatchesUseCase] 구현.
 * [now] 기준 만료된(응답 대기 상태로 만료 시각이 지난) 소개 id를 모아, 하나씩 제거([RemoveMatchPort])한다.
 * 제거는 매칭마다 독립 트랜잭션(core)으로 처리되며 코인을 지불한 참가자에게 절반이 환불된다.
 * 한 건의 실패가 다른 건에 전파되지 않도록 매칭 단위로 격리하고, 예외만 실패로 집계한다.
 */
@Service
class ExpireMatchesService(
	private val getExpiredMatchIdsPort: GetExpiredMatchIdsPort,
	private val removeMatchPort: RemoveMatchPort,
	private val timeGenerator: TimeGenerator,
) : ExpireMatchesUseCase {

	private val log: Logger = LoggerFactory.getLogger(javaClass)

	override fun run(): Int {
		val now: LocalDateTime = timeGenerator.now()
		val expiredMatchIds: List<Long> = getExpiredMatchIdsPort.findExpiredMatchIds(now)

		var removed = 0
		for (matchId: Long in expiredMatchIds) {
			try {
				removeMatchPort.remove(matchId)
				removed++
			} catch (e: Exception) {
				log.warn("매칭 만료 처리 실패 matchId={}", matchId, e)
			}
		}

		log.info("매칭 만료 처리 완료: 대상={}, 제거={}", expiredMatchIds.size, removed)
		return removed
	}
}
