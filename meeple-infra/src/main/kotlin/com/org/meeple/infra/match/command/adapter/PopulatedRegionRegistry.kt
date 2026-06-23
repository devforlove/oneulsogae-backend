package com.org.meeple.infra.match.command.adapter

import com.org.meeple.common.user.Gender
import com.org.meeple.infra.match.command.repository.MatchUserJpaRepository
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

/**
 * "특정 성별 매칭 유저가 있는 region" 집합을 성별별로 메모리에 캐시하는 match 도메인 인프라 컴포넌트.
 * 온보딩 후보 조회가 "찾는 성별(상대 성별)이 없는 region"을 지역 단위 순회에서 건너뛰는 데 쓴다.
 * (예: 남성 요청자는 여성 유저가 있는 region만 봐야 한다 — 남성만 있는 region은 헛조회다)
 * match_user를 읽는 관심사이므로 region 컴포넌트가 아닌 match 쪽에 둔다. (region이 match를 역참조하지 않게 함)
 * 기동 시 한 번 적재([warmUp])하고, 지역 매칭 스냅샷 갱신(일일 배치 등 [RegionProximityAdapter.refresh])에서 [refresh]로 다시 적재한다.
 */
@Component
class PopulatedRegionRegistry(
	private val matchUserJpaRepository: MatchUserJpaRepository,
) {

	@Volatile
	private var populatedRegionsByGender: Map<Gender, Set<Long>> = emptyMap()

	/** 기동 시 한 번 적재한다. (매칭 유저가 없어도 빈 집합으로 안전) */
	@PostConstruct
	fun warmUp() {
		refresh()
	}

	/** 성별별로 "그 성별 유저가 한 명이라도 있는 region_id" 집합을 다시 적재한다. */
	fun refresh() {
		populatedRegionsByGender = Gender.entries.associateWith { gender: Gender ->
			matchUserJpaRepository.findDistinctRegionIdsByGender(gender).toSet()
		}
	}

	/** [regionId]에 [gender] 매칭 유저가 있는지(스냅샷 기준). */
	fun contains(gender: Gender, regionId: Long): Boolean =
		regionId in (populatedRegionsByGender[gender] ?: emptySet())
}
