package com.org.oneulsogae.scheduler.user.command.application

import com.org.oneulsogae.scheduler.common.command.application.port.out.TimeGenerator
import com.org.oneulsogae.scheduler.user.command.application.port.`in`.RunPurgeWithdrawnUserBatchUseCase
import com.org.oneulsogae.scheduler.user.command.application.port.out.GetPurgableWithdrawnUserPort
import com.org.oneulsogae.scheduler.user.command.application.port.out.PurgeWithdrawnUserPort
import com.org.oneulsogae.scheduler.user.command.domain.PurgeWithdrawnUserBatchResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * [RunPurgeWithdrawnUserBatchUseCase] 구현. 탈퇴 유예([retentionDays]일)가 지난 사용자의 개인정보를 익명화한다.
 * 대상 id를 한 번 적재하고 건별로 [PurgeWithdrawnUserPort]에 위임한다(사용자당 트랜잭션 1개).
 * 한 건의 실패가 다른 건에 전파되지 않게 격리하고 예외만 failed로 집계한다.
 */
@Service
class PurgeWithdrawnUserBatchService(
	private val getPurgableWithdrawnUserPort: GetPurgableWithdrawnUserPort,
	private val purgeWithdrawnUserPort: PurgeWithdrawnUserPort,
	private val timeGenerator: TimeGenerator,
	@Value("\${oneulsogae.user.withdrawal.retention-days:10}") private val retentionDays: Long,
) : RunPurgeWithdrawnUserBatchUseCase {

	private val log: Logger = LoggerFactory.getLogger(javaClass)

	override fun run(): PurgeWithdrawnUserBatchResult {
		val cutoff: LocalDateTime = timeGenerator.now().minusDays(retentionDays)

		var purged = 0
		var failed = 0
		for (userId: Long in getPurgableWithdrawnUserPort.findUserIdsWithdrawnBefore(cutoff)) {
			try {
				purgeWithdrawnUserPort.purge(userId)
				purged++
			} catch (e: Exception) {
				failed++
				log.warn("탈퇴 계정 파기 실패 userId={}", userId, e)
			}
		}

		val result = PurgeWithdrawnUserBatchResult(purged = purged, failed = failed)
		log.info("탈퇴 계정 파기 배치 완료: {}", result)
		return result
	}
}
