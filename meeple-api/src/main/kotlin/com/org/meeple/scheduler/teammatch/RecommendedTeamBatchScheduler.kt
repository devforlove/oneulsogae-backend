package com.org.meeple.scheduler.teammatch

import com.org.meeple.scheduler.teammatch.command.adapter.RecommendedTeamBatchJob
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

	// [미팅 기능 미노출] 미팅(2:2 팀 매칭) 기능은 출시 시점에 노출하지 않으므로 팀 추천 일일 배치의
	// 크론 트리거를 주석 처리한다. (배치 로직 자체는 유지 — 노출 시 @Scheduled 주석만 해제하면 된다)
	// @Scheduled(cron = "\${meeple.match.recommended-team-batch.cron}", zone = "Asia/Seoul")
	fun runRecommendTeam() {
		recommendedTeamBatchJob.run()
	}
}
