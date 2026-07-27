package com.org.oneulsogae.core.user.query.service.port.`in`

import com.org.oneulsogae.core.user.query.dto.ReferralSummary

/** 내 추천 실적(추천한 친구 수·받은 코인) 조회 in-port. */
interface GetReferralSummaryUseCase {

	fun getSummary(userId: Long): ReferralSummary
}
