package com.org.oneulsogae.scheduler.lounge

import com.org.oneulsogae.scheduler.lounge.command.adapter.ExpireLoungeChatRequestBatchJob
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 만료 대화 신청 정리 배치 스케줄러(크론 트리거).
 * 정해진 크론 시각마다 oneulsogae-scheduler 모듈의 [ExpireLoungeChatRequestBatchJob]을 실행한다.
 * 실행 주기는 oneulsogae.lounge.chat-request-expire-batch.cron 프로퍼티로 조정한다.
 * 이 클래스는 api 프로세스에서 "언제 돌릴지"만 책임진다.
 */
@Component
class ExpireLoungeChatRequestBatchScheduler(
	private val expireLoungeChatRequestBatchJob: ExpireLoungeChatRequestBatchJob,
) {

	@Scheduled(cron = "\${oneulsogae.lounge.chat-request-expire-batch.cron}", zone = "Asia/Seoul")
	fun runExpireLoungeChatRequest() {
		expireLoungeChatRequestBatchJob.run()
	}
}
