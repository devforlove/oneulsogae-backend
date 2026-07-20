package com.org.oneulsogae.scheduler.common.command.application.port.out

/**
 * 가까운 순 regionId 목록의 순회 순서를 무작위로 흔들어 매칭 다양성을 주는 아웃포트.
 * 배치 로직이 무작위성을 직접 다루지 않고 이 인터페이스에 의존하게 해, 테스트에서 결정적으로 만들 수 있다.
 * (시각의 [TimeGenerator]와 같은 격리 의도. 구현은 scheduler가 직접 제공한다)
 */
fun interface RegionShuffler {

	/** 가까운 순 regionId 목록에서 앞 K개만 무작위로 섞은 새 목록을 반환한다. (K번째 이후는 순서 유지) */
	fun shuffleNearest(regionIds: List<Long>): List<Long>
}
