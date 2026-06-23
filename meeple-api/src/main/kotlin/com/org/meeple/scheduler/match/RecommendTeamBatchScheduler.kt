package com.org.meeple.scheduler.match

import com.org.meeple.scheduler.match.command.adapter.RecommendTeamBatchJob
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 팀 추천 일일 배치 스케줄러(크론 트리거).
 * 정해진 크론 시각마다 [RecommendTeamBatchJob]을 실행한다. 실행 주기는 meeple.match.recommend-team-batch.cron 프로퍼티로 조정한다.
 */
@Component
class RecommendTeamBatchScheduler(
	private val recommendTeamBatchJob: RecommendTeamBatchJob,
) {

	@Scheduled(cron = "\${meeple.match.recommend-team-batch.cron}", zone = "Asia/Seoul")
	fun runRecommendTeam() {
		recommendTeamBatchJob.run()
	}
}
