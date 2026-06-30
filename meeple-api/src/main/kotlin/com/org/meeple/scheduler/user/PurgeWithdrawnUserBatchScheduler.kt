package com.org.meeple.scheduler.user

import com.org.meeple.scheduler.user.command.adapter.PurgeWithdrawnUserBatchJob
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 탈퇴 계정 파기 배치 스케줄러(크론 트리거).
 * 정해진 크론 시각마다 meeple-scheduler 모듈의 [PurgeWithdrawnUserBatchJob]을 실행한다.
 * 실행 주기는 meeple.user.withdrawal.purge-batch.cron 프로퍼티로 조정한다.
 * 이 클래스는 api 프로세스에서 "언제 돌릴지"만 책임진다.
 */
@Component
class PurgeWithdrawnUserBatchScheduler(
	private val purgeWithdrawnUserBatchJob: PurgeWithdrawnUserBatchJob,
) {

	@Scheduled(cron = "\${meeple.user.withdrawal.purge-batch.cron}", zone = "Asia/Seoul")
	fun runPurge() {
		purgeWithdrawnUserBatchJob.run()
	}
}
