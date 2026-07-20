package com.org.oneulsogae.scheduler.teammatch

import com.org.oneulsogae.scheduler.teammatch.command.adapter.TeamMatchBatchJob
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 일일 팀 매칭 배치 스케줄러(크론 트리거).
 * 정해진 크론 시각마다 [TeamMatchBatchJob]을 실행한다. 실행 주기는 oneulsogae.match.team-match-batch.cron 프로퍼티로 조정한다.
 */
@Component
class TeamMatchBatchScheduler(
	private val teamMatchBatchJob: TeamMatchBatchJob,
) {

	@Scheduled(cron = "\${oneulsogae.match.team-match-batch.cron}", zone = "Asia/Seoul")
	fun runTeamMatch() {
		teamMatchBatchJob.run()
	}
}
