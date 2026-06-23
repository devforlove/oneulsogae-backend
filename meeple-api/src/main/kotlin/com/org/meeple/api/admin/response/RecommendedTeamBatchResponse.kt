package com.org.meeple.api.admin.response

import com.org.meeple.scheduler.match.command.domain.RecommendedTeamBatchResult

/**
 * 팀 추천 배치 실행 결과 응답. (관리자 수동 트리거)
 */
data class RecommendedTeamBatchResponse(
	/** 순회한 대상(팀 미소속 솔로 유저) 수. */
	val targets: Int,
	/** 추천을 적재한 수. */
	val recommended: Int,
	/** 후보 팀이 없어 건너뛴 수. */
	val skipped: Int,
	/** 예기치 못한 오류로 실패한 수. */
	val failed: Int,
) {
	companion object {
		fun of(result: RecommendedTeamBatchResult): RecommendedTeamBatchResponse =
			RecommendedTeamBatchResponse(
				targets = result.targets,
				recommended = result.recommended,
				skipped = result.skipped,
				failed = result.failed,
			)
	}
}
