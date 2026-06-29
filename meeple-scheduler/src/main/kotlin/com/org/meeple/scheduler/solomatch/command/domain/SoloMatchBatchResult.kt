package com.org.meeple.scheduler.solomatch.command.domain

/** 배치 실행 요약. */
data class SoloMatchBatchResult(
	/** 순회한 대상 수. */
	val targets: Int,
	/** 새로 소개한(매칭 생성) 수. */
	val recommended: Int,
	/** 이미 오늘 매칭 있음 / 후보 없음 / 프로필 미완성 등으로 건너뛴 수. */
	val skipped: Int,
	/** 예기치 못한 오류로 처리하지 못한 수. */
	val failed: Int,
)
