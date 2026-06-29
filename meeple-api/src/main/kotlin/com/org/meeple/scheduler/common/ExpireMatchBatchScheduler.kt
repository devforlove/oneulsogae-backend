package com.org.meeple.scheduler.common

import com.org.meeple.scheduler.common.command.adapter.ExpireMatchBatchJob
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 만료 매칭 정리 배치 스케줄러(크론 트리거).
 * 정해진 크론 시각마다 meeple-scheduler 모듈의 [ExpireMatchBatchJob]을 실행한다. 실행 주기는 meeple.match.expire-batch.cron 프로퍼티로 조정한다.
 * 이 클래스는 api 프로세스에서 "언제 돌릴지"만 책임진다.
 */
@Component
class ExpireMatchBatchScheduler(
	private val expireMatchBatchJob: ExpireMatchBatchJob,
) {

	@Scheduled(cron = "\${meeple.match.expire-batch.cron}", zone = "Asia/Seoul")
	fun runExpireMatch() {
		expireMatchBatchJob.run()
	}
}
