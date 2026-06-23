package com.org.meeple.scheduler.match.command.application.port.out

import com.org.meeple.scheduler.match.command.domain.MatchPoolGroup

/**
 * 매칭 후보 풀을 저장하는 아웃포트. (성별, 지역=regionId) 그룹 풀을 적재한다.
 * 구현은 infra 레이어의 Redis 어댑터가 담당한다.
 */
interface SaveMatchPoolPort {

	/** (성별, 지역) 그룹 풀을 저장한다. (match:pool:{gender}:{regionId}) */
	fun save(group: MatchPoolGroup)
}
