package com.org.meeple.admin.gathering.query.service.port.`in`

import com.org.meeple.admin.gathering.query.dto.AdminGatheringDetailView
import com.org.meeple.admin.gathering.query.dto.AdminGatheringPage

/** 어드민 모임 조회 유스케이스. (조회 전용) */
interface GetAdminGatheringsUseCase {

	/** 모임을 최신순으로 page(0부터)·size 페이징 조회한다. */
	fun getGatherings(page: Int, size: Int): AdminGatheringPage

	/** 모임 상세를 id로 조회한다. 없으면 GATHERING_NOT_FOUND. */
	fun getGathering(id: Long): AdminGatheringDetailView
}
