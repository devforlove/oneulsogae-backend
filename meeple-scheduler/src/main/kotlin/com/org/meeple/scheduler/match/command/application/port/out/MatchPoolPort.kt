package com.org.meeple.scheduler.match.command.application.port.out

import com.org.meeple.common.user.Gender

/**
 * 매칭 후보 풀을 소비하는 아웃포트.
 * 풀 적재는 [SaveMatchPoolPort]가, 소비(꺼내기/되돌리기/제거)는 이 포트가 담당한다.
 * (성별, 지역=regionId) 풀 단위로 소비한다. 구현은 infra 레이어의 Redis 어댑터가 맡는다.
 */
interface MatchPoolPort {

	/**
	 * (성별, 지역) 풀에서 후보 한 명을 꺼낸다(pop). 꺼낸 사용자는 풀에서 제거된다.
	 * 풀이 비어 있으면 null. (Redis Set이라 어떤 한 명이 나오는지는 무작위 — 같은 지역 내 동순위 타이브레이크)
	 */
	fun pop(gender: Gender, regionId: Long): Long?

	/** [pop]했지만 매칭하지 못한(재소개 이력 충돌 등) 후보들을 풀에 되돌린다. */
	fun pushBack(gender: Gender, regionId: Long, userIds: List<Long>)

	/** 매칭이 성사된 사용자를 (성별, 지역) 풀에서 제거해, 같은 배치에서 다시 후보로 뽑히지 않게 한다. */
	fun remove(gender: Gender, regionId: Long, userId: Long)
}
