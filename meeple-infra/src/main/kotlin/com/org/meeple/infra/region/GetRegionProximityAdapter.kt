package com.org.meeple.infra.region

import com.org.meeple.core.common.region.GetRegionProximityPort
import org.springframework.stereotype.Component

/**
 * [GetRegionProximityPort]의 인프라 구현. core 포트를 infra 근접 스냅샷([RegionProximityRegistry])에 잇는다.
 * (온보딩·배치와 같은 스냅샷을 공유한다)
 */
@Component
class GetRegionProximityAdapter(
	private val regionProximityRegistry: RegionProximityRegistry,
) : GetRegionProximityPort {

	override fun nearbyRegionIds(regionId: Long): List<Long> =
		regionProximityRegistry.nearbyRegionIds(regionId)
}
