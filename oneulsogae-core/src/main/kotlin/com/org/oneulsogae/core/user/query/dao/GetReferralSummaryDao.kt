package com.org.oneulsogae.core.user.query.dao

import com.org.oneulsogae.core.user.query.dto.ReferralSummary

/** 추천 실적 조회 dao(query out-port 인터페이스). QueryDSL 구현은 infra가 담당한다. */
interface GetReferralSummaryDao {

	/** [referrerUserId]가 추천인으로서 받은 보상의 건수·코인 합계. 지급 이력이 없으면 0/0. */
	fun findSummary(referrerUserId: Long): ReferralSummary
}
