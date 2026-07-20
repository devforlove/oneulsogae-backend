package com.org.oneulsogae.api.region.response

import com.org.oneulsogae.core.region.query.dto.RegionView

/**
 * 활동지역 응답. 클라이언트는 id를 포함한 지역 목록을 받아, 서버 요청 시 regionId로 지역을 지정한다.
 */
data class RegionResponse(
	val id: Long,
	val sido: String,
	val sigungu: String,
) {
	companion object {
		fun listOf(regions: List<RegionView>): List<RegionResponse> =
			regions.map { region: RegionView -> RegionResponse(region.id, region.sido, region.sigungu) }
	}
}
