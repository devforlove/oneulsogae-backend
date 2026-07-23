package com.org.oneulsogae.scheduler.common.command.domain

/**
 * 만료 정리 배치 실행 결과 요약. (정리된 솔로/팀 매칭·라운지 대화 신청 수, 건별 실패 수)
 */
data class ExpireMatchBatchResult(
	val soloExpired: Int,
	val teamExpired: Int,
	val loungeChatRequestExpired: Int,
	val soloFailed: Int,
	val teamFailed: Int,
	val loungeChatRequestFailed: Int,
)
