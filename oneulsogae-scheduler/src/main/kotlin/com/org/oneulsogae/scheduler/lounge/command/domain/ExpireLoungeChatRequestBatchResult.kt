package com.org.oneulsogae.scheduler.lounge.command.domain

/**
 * 만료 대화 신청 정리 배치 실행 결과 요약. (정리된 신청 수, 건별 실패 수)
 */
data class ExpireLoungeChatRequestBatchResult(
	val expired: Int,
	val failed: Int,
)
