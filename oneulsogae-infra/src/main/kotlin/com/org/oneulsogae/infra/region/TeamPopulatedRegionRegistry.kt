package com.org.oneulsogae.infra.region

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.teammatch.command.repository.TeamJpaRepository
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component

/**
 * "특정 성별 결성(ACTIVE) 팀이 있는 region" 집합을 성별별로 메모리에 캐시하는 match 도메인 인프라 컴포넌트.
 * 가까운 팀 추천([NearestTeamAdapter])이 "찾는 성별(상대 성별) 팀이 없는 region"을 지역 단위 순회에서 건너뛰는 데 쓴다.
 * (개인 거주지 분포인 [PopulatedRegionRegistry]와 달리 팀의 활동지역(team.region_id) 분포를 본다 — 팀 추천 필터는 team.region_id 기준이라야 정확하다)
 * 기동 시 한 번 적재([warmUp])하고, 지역 매칭 스냅샷 갱신([RegionProximityAdapter.refresh])에서 [refresh]로 다시 적재한다.
 */
@Component
class TeamPopulatedRegionRegistry(
	private val teamJpaRepository: TeamJpaRepository,
) {

	@Volatile
	private var populatedRegionsByGender: Map<Gender, Set<Long>> = emptyMap()

	/** 기동 시 한 번 적재한다. (팀이 없어도 빈 집합으로 안전) */
	@PostConstruct
	fun warmUp() {
		refresh()
	}

	/** 성별별로 "그 성별 결성(ACTIVE) 팀이 하나라도 있는 region_id" 집합을 다시 적재한다. */
	fun refresh() {
		populatedRegionsByGender = Gender.entries.associateWith { gender: Gender ->
			teamJpaRepository.findDistinctActiveRegionIdsByGender(gender).toSet()
		}
	}

	/** [regionId]에 [gender]의 결성(ACTIVE) 팀이 있는지(스냅샷 기준). */
	fun contains(gender: Gender, regionId: Long): Boolean =
		regionId in (populatedRegionsByGender[gender] ?: emptySet())
}
