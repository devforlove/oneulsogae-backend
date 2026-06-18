package com.org.meeple.scheduler.match

import com.org.meeple.scheduler.match.command.application.MatchExpireJob
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 만료된 소개 정리 스케줄러(크론 트리거).
 * 정해진 크론 시각마다 meeple-scheduler 모듈의 [MatchExpireJob]을 실행한다. 실행 주기는 meeple.match.expire.cron 프로퍼티로 조정한다.
 * 실제 정리 로직은 [MatchExpireJob]에 있고, 이 클래스는 api 프로세스에서 "언제 돌릴지"만 책임진다.
 */
@Component
class MatchExpireScheduler(
	private val matchExpireJob: MatchExpireJob,
) {

	@Scheduled(cron = "\${meeple.match.expire.cron}", zone = "Asia/Seoul")
	fun expireMatches() {
		matchExpireJob.run()
	}
}
