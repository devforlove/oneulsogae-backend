package com.org.meeple.infra.match.command.adapter

import com.org.meeple.infra.match.command.repository.MatchUserJpaRepository
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

/**
 * "매칭 유저가 있는 region" 집합을 메모리에 캐시하는 match 도메인 인프라 컴포넌트.
 * 온보딩 후보 조회가 빈 region을 지역 단위 순회에서 건너뛰는 데 쓴다.
 * match_user를 읽는 관심사이므로 region 컴포넌트가 아닌 match 쪽에 둔다. (region이 match를 역참조하지 않게 함)
 * 기동 시 한 번 적재([warmUp])하고, 지역 매칭 스냅샷 갱신(일일 배치 등 [RegionProximityAdapter.refresh])에서 [refresh]로 다시 적재한다.
 */
@Component
class PopulatedRegionRegistry(
	private val matchUserJpaRepository: MatchUserJpaRepository,
) {

	@Volatile
	private var populatedRegionIds: Set<Long> = emptySet()

	/** 기동 시 한 번 적재한다. (매칭 유저가 없어도 빈 집합으로 안전) */
	@PostConstruct
	fun warmUp() {
		refresh()
	}

	/** match_user에 한 명이라도 있는 region_id 집합을 다시 적재한다. */
	fun refresh() {
		populatedRegionIds = matchUserJpaRepository.findDistinctRegionIds().toSet()
	}

	/** [regionId]에 매칭 유저가 있는지(스냅샷 기준). */
	fun contains(regionId: Long): Boolean =
		regionId in populatedRegionIds
}
