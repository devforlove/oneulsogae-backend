package com.org.oneulsogae.infra.region

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.core.common.time.TimeGenerator
import com.org.oneulsogae.infra.matchuser.command.repository.MatchUserJpaRepository
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * "특정 성별이면서 최근 로그인한 매칭 유저가 있는 region" 집합을 성별별로 메모리에 캐시하는 match 도메인 인프라 컴포넌트.
 * 온보딩 후보 조회가 "찾는 성별(상대 성별)의 신선 후보가 없는 region"을 지역 단위 순회에서 건너뛰는 데 쓴다.
 * (예: 남성 요청자는 최근 로그인한 여성 유저가 있는 region만 봐야 한다 — 그렇지 않은 region은 헛조회다)
 * 후보 조회가 [RECENT_LOGIN_WEEKS]주 이내 로그인만 후보로 삼으므로, 스냅샷도 같은 기준으로 좁힌다. (스냅샷이 후보 조회보다 좁지 않아 누락 없음)
 * match_user를 읽는 관심사이므로 region 컴포넌트가 아닌 match 쪽에 둔다. (region이 match를 역참조하지 않게 함)
 * 기동 시 한 번 적재([warmUp])하고, 지역 매칭 스냅샷 갱신(일일 배치 등 [RegionProximityAdapter.refresh])에서 [refresh]로 다시 적재한다.
 */
@Component
class PopulatedRegionRegistry(
	private val matchUserJpaRepository: MatchUserJpaRepository,
	private val timeGenerator: TimeGenerator,
) {

	@Volatile
	private var populatedRegionsByGender: Map<Gender, Set<Long>> = emptyMap()

	/** 기동 시 한 번 적재한다. (매칭 유저가 없어도 빈 집합으로 안전) */
	@PostConstruct
	fun warmUp() {
		refresh()
	}

	/** 성별별로 "그 성별이면서 최근 [RECENT_LOGIN_WEEKS]주 이내 로그인한 유저가 한 명이라도 있는 region_id" 집합을 다시 적재한다. */
	fun refresh() {
		val loginAfter: LocalDateTime = timeGenerator.now().minusWeeks(RECENT_LOGIN_WEEKS)
		populatedRegionsByGender = Gender.entries.associateWith { gender: Gender ->
			matchUserJpaRepository.findDistinctRegionIdsByGender(gender, loginAfter).toSet()
		}
	}

	/** [regionId]에 [gender]의 최근 로그인 매칭 유저가 있는지(스냅샷 기준). */
	fun contains(gender: Gender, regionId: Long): Boolean =
		regionId in (populatedRegionsByGender[gender] ?: emptySet())

	companion object {

		/** 후보로 인정하는 최근 로그인 기간(주). 후보 조회(RecommendMatchService 등)와 같은 기준으로 스냅샷을 좁힌다. */
		private const val RECENT_LOGIN_WEEKS = 2L
	}
}
