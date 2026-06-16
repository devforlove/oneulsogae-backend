package com.org.meeple.scheduler.match.command.application.port.out

import com.org.meeple.scheduler.match.command.domain.MatchPoolByGender
import com.org.meeple.scheduler.match.command.domain.MatchPoolGroup

/**
 * 매칭 후보 풀을 저장하는 아웃포트.
 * (성별, 지역) 그룹 풀과 지역 무관 성별 풀을 각각 적재한다.
 * 구현은 infra 레이어의 Redis 어댑터가 담당한다. (core는 Redis 등 인프라 세부에 의존하지 않는다)
 */
interface SaveMatchPoolPort {

	/** (성별, 지역) 그룹 풀을 저장한다. (match:pool:{gender}:{regionCode}) */
	fun save(group: MatchPoolGroup)

	/** 지역과 무관하게 성별만으로 묶은 풀을 저장한다. (match:pool:{gender}) */
	fun saveByGender(pool: MatchPoolByGender)
}
