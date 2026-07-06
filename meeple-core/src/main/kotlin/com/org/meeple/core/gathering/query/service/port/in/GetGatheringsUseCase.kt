package com.org.meeple.core.gathering.query.service.port.`in`

import com.org.meeple.core.gathering.query.dto.GatheringDetailView
import com.org.meeple.core.gathering.query.dto.GroupedGatherings

/** 유저용 모임 조회 인포트(유스케이스). */
interface GetGatheringsUseCase {

	/** 모집중 모임을 타입별로 그룹핑해 조회한다. (모든 타입 포함, 타입 내 최신 등록순) */
	fun getGatherings(): GroupedGatherings

	/** 모집중 모임 한 건의 상세를 id로 조회한다. 없거나 모집중이 아니면 404(GATHERING-001). */
	fun getGathering(id: Long): GatheringDetailView
}
