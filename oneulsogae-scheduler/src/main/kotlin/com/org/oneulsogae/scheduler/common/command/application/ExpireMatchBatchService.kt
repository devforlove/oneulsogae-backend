package com.org.oneulsogae.scheduler.common.command.application

import com.org.oneulsogae.scheduler.common.command.application.port.`in`.RunExpireMatchBatchUseCase
import com.org.oneulsogae.scheduler.common.command.application.port.out.ExpireMatchPort
import com.org.oneulsogae.scheduler.common.command.application.port.out.GetExpiredMatchPort
import com.org.oneulsogae.scheduler.common.command.application.port.out.TimeGenerator
import com.org.oneulsogae.scheduler.common.command.domain.ExpireMatchBatchResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * [RunExpireMatchBatchUseCase] 구현. 매시간 도는 만료 정리 배치.
 * 만료된 솔로·팀 매칭과 라운지 대화 신청 id를 한 번 적재하고, 건별로 [ExpireMatchPort]에 위임한다(1건당 트랜잭션 1개).
 * 한 건의 실패가 다른 건에 전파되지 않도록 건 단위로 격리하고, 예외만 failed로 집계한다.
 */
@Service
class ExpireMatchBatchService(
	private val getExpiredMatchPort: GetExpiredMatchPort,
	private val expireMatchPort: ExpireMatchPort,
	private val timeGenerator: TimeGenerator,
) : RunExpireMatchBatchUseCase {

	private val log: Logger = LoggerFactory.getLogger(javaClass)

	override fun run(): ExpireMatchBatchResult {
		val now: LocalDateTime = timeGenerator.now()

		var soloExpired = 0
		var soloFailed = 0
		for (matchId: Long in getExpiredMatchPort.findExpiredSoloMatchIds(now)) {
			try {
				expireMatchPort.expireSoloMatch(matchId)
				soloExpired++
			} catch (e: Exception) {
				soloFailed++
				log.warn("만료 솔로 매칭 정리 실패 matchId={}", matchId, e)
			}
		}

		var teamExpired = 0
		var teamFailed = 0
		for (teamMatchId: Long in getExpiredMatchPort.findExpiredTeamMatchIds(now)) {
			try {
				expireMatchPort.expireTeamMatch(teamMatchId)
				teamExpired++
			} catch (e: Exception) {
				teamFailed++
				log.warn("만료 팀 매칭 정리 실패 teamMatchId={}", teamMatchId, e)
			}
		}

		var loungeChatRequestExpired = 0
		var loungeChatRequestFailed = 0
		for (requestId: Long in getExpiredMatchPort.findExpiredLoungeChatRequestIds(now)) {
			try {
				expireMatchPort.expireLoungeChatRequest(requestId)
				loungeChatRequestExpired++
			} catch (e: Exception) {
				loungeChatRequestFailed++
				log.warn("만료 대화 신청 정리 실패 requestId={}", requestId, e)
			}
		}

		val result: ExpireMatchBatchResult = ExpireMatchBatchResult(
			soloExpired = soloExpired,
			teamExpired = teamExpired,
			loungeChatRequestExpired = loungeChatRequestExpired,
			soloFailed = soloFailed,
			teamFailed = teamFailed,
			loungeChatRequestFailed = loungeChatRequestFailed,
		)
		log.info("만료 정리 배치 완료: {}", result)
		return result
	}
}
