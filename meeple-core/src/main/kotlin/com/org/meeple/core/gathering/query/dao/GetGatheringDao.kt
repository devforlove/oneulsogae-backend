package com.org.meeple.core.gathering.query.dao

import com.org.meeple.core.gathering.query.dto.GatheringViews

/**
 * 유저용 모임 조회 dao(query out-port). (조회 전용 read model 반환)
 * 모집중(RECRUITING) 모임만 모임 일시 임박순으로 조회한다. 실제 구현은 infra 레이어가 담당한다.
 */
interface GetGatheringDao {

	/** 모집중 모임을 gatheringAt 오름차순(임박순)으로 조회한다. (imageUrl은 서비스가 채운다) */
	fun findRecruitingOrderByGatheringAt(): GatheringViews
}
