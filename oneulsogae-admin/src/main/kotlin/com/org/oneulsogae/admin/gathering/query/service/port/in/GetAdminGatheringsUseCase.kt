package com.org.oneulsogae.admin.gathering.query.service.port.`in`

import com.org.oneulsogae.admin.gathering.query.dto.AdminGatheringDetailView
import com.org.oneulsogae.admin.gathering.query.dto.AdminGatheringPage
import com.org.oneulsogae.common.gathering.GatheringStatus
import com.org.oneulsogae.common.gathering.GatheringType

/** 어드민 모임 조회 유스케이스. (조회 전용) */
interface GetAdminGatheringsUseCase {

	/** 모임을 최신순으로 page(0부터)·size 페이징 조회한다. [status]·[type]이 null이면 해당 필터를 적용하지 않는다. */
	fun getGatherings(page: Int, size: Int, status: GatheringStatus?, type: GatheringType?): AdminGatheringPage

	/** 모임 상세를 id로 조회한다. 없으면 GATHERING_NOT_FOUND. */
	fun getGathering(id: Long): AdminGatheringDetailView
}
