package com.org.meeple.scheduler.solomatch.query.dao

import com.org.meeple.matching.MatchScoringProfile
import java.time.LocalDate

/**
 * 이상형 우선순위 스코어링 프로필 조회 포트. 배치 시작 시 대상 userId 집합에 대해 1회 적재한다.
 * 구현(infra)이 user_details + user_ideal_types를 조인해 [today] 기준 나이까지 계산한 read model로 투영한다.
 */
interface GetMatchScoringProfileDao {

	/** [userIds]의 스코어링 프로필을 userId→프로필 맵으로 돌려준다. (user_details가 없는 userId는 결과에 없음) */
	fun load(userIds: Set<Long>, today: LocalDate): Map<Long, MatchScoringProfile>
}
