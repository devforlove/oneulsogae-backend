package com.org.meeple.infra.region

import com.org.meeple.infra.region.entity.RegionEntity
import com.org.meeple.infra.region.repository.RegionJpaRepository
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

/**
 * [RegionProximity] 스냅샷을 메모리에 캐시하는 인프라 컴포넌트.
 * regions는 정적 참조 데이터라 기동 시 한 번 적재([warmUp])하고, 일일 배치 시작 등에서 [refresh]로 갱신한다.
 * 두 매칭 경로(온보딩 후보 어댑터·배치 근접 포트 어댑터)가 같은 스냅샷을 공유한다.
 */
@Component
class RegionProximityRegistry(
	private val regionJpaRepository: RegionJpaRepository,
) {

	@Volatile
	private var proximity: RegionProximity = RegionProximity.EMPTY

	/** 기동 시 한 번 적재한다. (regions가 비어 있어도 EMPTY로 안전) */
	@PostConstruct
	fun warmUp() {
		refresh()
	}

	/** regions 좌표를 다시 읽어 근접 스냅샷을 교체한다. (참조 데이터 변경 반영) */
	fun refresh() {
		val points: List<RegionPoint> = regionJpaRepository.findAll()
			.map { region: RegionEntity ->
				RegionPoint(
					regionId = region.id!!,
					latitude = region.latitude,
					longitude = region.longitude,
				)
			}
		proximity = RegionProximity.from(points)
	}

	/** [regionId]에서 가까운 순으로 정렬한 전체 regionId. (스냅샷 위임) */
	fun nearbyRegionIds(regionId: Long): List<Long> =
		proximity.nearbyRegionIds(regionId)
}
