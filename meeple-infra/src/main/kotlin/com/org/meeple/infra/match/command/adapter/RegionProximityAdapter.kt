package com.org.meeple.infra.match.command.adapter

import com.org.meeple.infra.region.RegionProximityRegistry
import com.org.meeple.scheduler.match.command.application.port.out.RegionProximityPort
import org.springframework.stereotype.Component

/**
 * [RegionProximityPort]의 인프라 구현. scheduler 포트를 infra 스냅샷들로 잇는다.
 * (scheduler는 core/infra를 모르므로, regions 좌표를 아는 infra가 근접 계산을 제공한다)
 * [refresh]는 지역 매칭 스냅샷 둘(근접 [RegionProximityRegistry] + 유저 분포 [PopulatedRegionRegistry])을 함께 갱신한다.
 * (일일 배치가 시작 시 한 번 호출 → 온보딩이 쓰는 두 스냅샷이 매일 최신화된다)
 */
@Component
class RegionProximityAdapter(
	private val regionProximityRegistry: RegionProximityRegistry,
	private val populatedRegionRegistry: PopulatedRegionRegistry,
) : RegionProximityPort {

	override fun refresh() {
		regionProximityRegistry.refresh()
		populatedRegionRegistry.refresh()
	}

	override fun nearbyRegionIds(regionId: Long): List<Long> =
		regionProximityRegistry.nearbyRegionIds(regionId)
}
