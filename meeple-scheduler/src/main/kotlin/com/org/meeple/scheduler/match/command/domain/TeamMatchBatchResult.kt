package com.org.meeple.scheduler.match.command.domain

/**
 * 팀 매칭 배치 실행 결과 요약. (순회 대상 팀 수, 신규 소개 수, 건너뜀, 실패)
 */
data class TeamMatchBatchResult(
	val targets: Int,
	val recommended: Int,
	val skipped: Int,
	val failed: Int,
)
