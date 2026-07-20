package com.org.oneulsogae.scheduler.common.command.domain

/**
 * 만료 매칭 정리 배치 실행 결과 요약. (정리된 솔로/팀 수, 건별 실패 수)
 */
data class ExpireMatchBatchResult(
	val soloExpired: Int,
	val teamExpired: Int,
	val soloFailed: Int,
	val teamFailed: Int,
)
