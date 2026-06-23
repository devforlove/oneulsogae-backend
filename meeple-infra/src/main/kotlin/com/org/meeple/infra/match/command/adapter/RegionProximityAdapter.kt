package com.org.meeple.infra.match.command.adapter

import com.org.meeple.infra.region.RegionProximityRegistry
import com.org.meeple.scheduler.match.command.application.port.out.RegionProximityPort
import org.springframework.stereotype.Component

/**
 * [RegionProximityPort]의 인프라 구현. scheduler 포트를 [RegionProximityRegistry](infra)로 잇는다.
 * (scheduler는 core/infra를 모르므로, regions 좌표를 아는 infra가 근접 계산을 제공한다)
 */
@Component
class RegionProximityAdapter(
	private val regionProximityRegistry: RegionProximityRegistry,
) : RegionProximityPort {

	override fun refresh() {
		regionProximityRegistry.refresh()
	}

	override fun nearbyRegionIds(regionId: Long): List<Long> =
		regionProximityRegistry.nearbyRegionIds(regionId)
}
