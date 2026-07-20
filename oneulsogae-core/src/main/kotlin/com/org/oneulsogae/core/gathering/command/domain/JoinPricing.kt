package com.org.oneulsogae.core.gathering.command.domain

/** 참가 접수 시 확정된 가격 정보. [earlyBirdApplied]가 true면 얼리버드 여분을 소진했다. */
data class JoinPricing(
	val amount: Int,
	val earlyBirdApplied: Boolean,
)
