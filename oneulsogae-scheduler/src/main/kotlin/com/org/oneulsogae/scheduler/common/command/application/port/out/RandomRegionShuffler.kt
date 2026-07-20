package com.org.oneulsogae.scheduler.common.command.application.port.out

import org.springframework.stereotype.Component
import kotlin.random.Random

/**
 * 가까운 순 regionId 목록의 앞 [NEAREST_SHUFFLE_COUNT]개만 무작위로 섞는 [RegionShuffler] 기본 구현.
 * 매칭 상대를 "가장 가까운 N개 지역 안"으로 한정하되 그중 순서를 흔들어, 항상 같은 최근접 후보만 뽑히지 않게 한다.
 * (인프라 의존이 없어 scheduler 모듈에서 직접 제공한다. [random]은 테스트에서 시드 고정용으로 주입한다)
 */
@Component
class RandomRegionShuffler(
	private val random: Random = Random.Default,
) : RegionShuffler {

	override fun shuffleNearest(regionIds: List<Long>): List<Long> {
		if (regionIds.size <= 1) return regionIds
		val head: List<Long> = regionIds.take(NEAREST_SHUFFLE_COUNT).shuffled(random)
		val tail: List<Long> = regionIds.drop(NEAREST_SHUFFLE_COUNT)
		return head + tail
	}

	companion object {
		/** 순서를 섞을 최근접 지역 개수. */
		private const val NEAREST_SHUFFLE_COUNT = 10
	}
}
