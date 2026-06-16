package com.org.meeple.api.admin.response

import com.org.meeple.scheduler.match.command.domain.MatchBatchResult

/**
 * 매칭 배치 실행 결과 응답. (관리자 수동 트리거)
 * scheduler 도메인 타입([MatchBatchResult])을 직접 노출하지 않고 표현 계층 DTO로 옮긴다.
 */
data class MatchBatchResponse(
	/** 순회한 대상 사용자 수. */
	val targets: Int,
	/** 실제로 소개가 생성된 수. */
	val recommended: Int,
	/** 대상이 아니거나 후보가 없어 건너뛴 수. */
	val skipped: Int,
	/** 예기치 못한 오류로 실패한 수. */
	val failed: Int,
) {
	companion object {
		fun of(result: MatchBatchResult): MatchBatchResponse =
			MatchBatchResponse(
				targets = result.targets,
				recommended = result.recommended,
				skipped = result.skipped,
				failed = result.failed,
			)
	}
}
