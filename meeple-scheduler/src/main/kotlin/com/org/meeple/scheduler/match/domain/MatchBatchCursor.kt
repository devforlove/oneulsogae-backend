package com.org.meeple.scheduler.match.domain

import java.time.LocalDateTime

/** (lastLoginAt, userId) 복합 키셋 커서. 직전 페이지의 마지막 대상 위치를 가리킨다. */
data class MatchBatchCursor(
	val lastLoginAt: LocalDateTime,
	val userId: Long,
)
