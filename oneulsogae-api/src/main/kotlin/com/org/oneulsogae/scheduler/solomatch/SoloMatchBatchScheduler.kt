package com.org.oneulsogae.scheduler.solomatch

import com.org.oneulsogae.scheduler.solomatch.command.adapter.SoloMatchBatchJob
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 일일 매칭 배치 스케줄러(크론 트리거).
 * 정해진 크론 시각마다 oneulsogae-scheduler 모듈의 [SoloMatchBatchJob]을 실행한다. 실행 주기는 oneulsogae.match.batch.cron 프로퍼티로 조정한다.
 * 실제 배치 실행 로직은 [SoloMatchBatchJob]에 있고, 이 클래스는 api 프로세스에서 "언제 돌릴지"만 책임진다.
 */
@Component
class SoloMatchBatchScheduler(
	private val soloMatchBatchJob: SoloMatchBatchJob,
) {

	@Scheduled(cron = "\${oneulsogae.match.batch.cron}", zone = "Asia/Seoul")
	fun runSoloMatch() {
		soloMatchBatchJob.run()
	}
}
