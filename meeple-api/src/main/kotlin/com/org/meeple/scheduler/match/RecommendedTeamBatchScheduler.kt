package com.org.meeple.scheduler.match

import com.org.meeple.scheduler.match.command.adapter.RecommendedTeamBatchJob
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 팀 추천 일일 배치 스케줄러(크론 트리거).
 * 정해진 크론 시각마다 [RecommendedTeamBatchJob]을 실행한다. 실행 주기는 meeple.match.recommended-team-batch.cron 프로퍼티로 조정한다.
 */
@Component
class RecommendedTeamBatchScheduler(
	private val recommendedTeamBatchJob: RecommendedTeamBatchJob,
) {

	@Scheduled(cron = "\${meeple.match.recommended-team-batch.cron}", zone = "Asia/Seoul")
	fun runRecommendTeam() {
		recommendedTeamBatchJob.run()
	}
}
