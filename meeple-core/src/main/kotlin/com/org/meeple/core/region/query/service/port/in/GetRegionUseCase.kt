package com.org.meeple.core.region.query.service.port.`in`

import com.org.meeple.core.region.query.dto.RegionView

/**
 * 단건 지역을 조회하는 유스케이스(인포트). (활동지역 id로 지역 정보를 얻는다)
 */
interface GetRegionUseCase {

	/** id로 지역을 조회한다. 없으면 [com.org.meeple.core.region.RegionErrorCode.REGION_NOT_FOUND]. */
	fun getById(id: Long): RegionView
}
