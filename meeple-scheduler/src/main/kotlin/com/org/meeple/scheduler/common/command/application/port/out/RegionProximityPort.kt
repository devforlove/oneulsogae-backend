package com.org.meeple.scheduler.common.command.application.port.out

/**
 * 배치가 지역 근접 순서를 얻기 위한 아웃포트. (scheduler는 core·infra에 의존하지 않으므로 자기 포트만 정의)
 * 구현은 infra 레이어가 [com.org.meeple.infra.region.RegionProximityRegistry] 위임으로 담당한다.
 */
interface RegionProximityPort {

	/** 근접 스냅샷을 최신 regions로 갱신한다. (배치 시작 시 1회 호출) */
	fun refresh()

	/** [regionId]에서 가까운 순으로 정렬한 전체 regionId. 좌표를 모르는 지역이면 빈 리스트. */
	fun nearbyRegionIds(regionId: Long): List<Long>
}
