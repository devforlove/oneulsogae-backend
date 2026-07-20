package com.org.oneulsogae.core.region.query.service.port.`in`

import com.org.oneulsogae.core.region.query.dto.RegionView

/**
 * 전체 지역 목록을 조회하는 유스케이스(인포트). (활동지역 선택 옵션용)
 */
interface GetRegionsUseCase {

	fun getAll(): List<RegionView>
}
