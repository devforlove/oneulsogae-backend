package com.org.meeple.infra.region

import com.org.meeple.infra.match.command.repository.MatchUserJpaRepository
import com.org.meeple.infra.region.entity.RegionEntity
import com.org.meeple.infra.region.repository.RegionJpaRepository
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

/**
 * [RegionProximity] 스냅샷과 "매칭 유저가 있는 region" 집합을 메모리에 캐시하는 인프라 컴포넌트.
 * regions는 정적 참조 데이터라 기동 시 한 번 적재([warmUp])하고, 일일 배치 시작 등에서 [refresh]로 갱신한다.
 * 두 매칭 경로(온보딩 후보 어댑터·배치 근접 포트 어댑터)가 같은 스냅샷을 공유한다.
 */
@Component
class RegionProximityRegistry(
	private val regionJpaRepository: RegionJpaRepository,
	private val matchUserJpaRepository: MatchUserJpaRepository,
) {

	@Volatile
	private var proximity: RegionProximity = RegionProximity.EMPTY

	@Volatile
	private var populatedRegionIds: Set<Long> = emptySet()

	/** 기동 시 한 번 적재한다. (regions/매칭 유저가 비어 있어도 안전) */
	@PostConstruct
	fun warmUp() {
		refresh()
	}

	/** regions 좌표와 "유저 있는 region" 집합을 다시 읽어 스냅샷을 교체한다. (참조/유저 데이터 변경 반영) */
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
		populatedRegionIds = matchUserJpaRepository.findDistinctRegionIds().toSet()
	}

	/** [regionId]에서 가까운 순으로 정렬한 전체 regionId. (스냅샷 위임) */
	fun nearbyRegionIds(regionId: Long): List<Long> =
		proximity.nearbyRegionIds(regionId)

	/**
	 * [regionId]에서 가까운 순 중 "매칭 유저가 있는" region만 추린 목록.
	 * 빈 region을 지역 단위 조회에서 건너뛰기 위한 최적화다. (스냅샷 갱신 전 새로 유저가 생긴 region은 누락될 수 있으나,
	 * 실제 후보 조회는 진짜 데이터를 보므로 정확성은 깨지지 않는다 — 누락분은 호출 측 랜덤 폴백이 받친다)
	 */
	fun nearbyPopulatedRegionIds(regionId: Long): List<Long> =
		proximity.nearbyRegionIds(regionId).filter { id: Long -> id in populatedRegionIds }
}
