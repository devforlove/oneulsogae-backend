package com.org.meeple.scheduler.user.command.domain

/** 파기 배치 결과 집계. */
data class PurgeWithdrawnUserBatchResult(
	val purged: Int,
	val failed: Int,
)
