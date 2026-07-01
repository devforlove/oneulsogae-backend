package com.org.meeple.core.common.region

/**
 * 지역 근접 순위 조회 out-port. [regionId]에서 가까운 순으로 정렬된 전체 regionId를 반환한다.
 * infra가 RegionProximityRegistry(온보딩·배치와 공유 스냅샷)에 위임해 구현한다.
 */
interface GetRegionProximityPort {
	fun nearbyRegionIds(regionId: Long): List<Long>
}
