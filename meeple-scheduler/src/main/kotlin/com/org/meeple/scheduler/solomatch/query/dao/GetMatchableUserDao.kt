package com.org.meeple.scheduler.solomatch.query.dao

import com.org.meeple.scheduler.solomatch.query.dto.MatchableUser
import java.time.LocalDateTime

/**
 * 일일 배치의 후보 풀을 적재하는 dao. (조회 전용)
 * scheduler는 core/infra에 의존하지 않으므로 자기 dao만 정의하고, 구현은 match_user를 아는 infra가 담당한다.
 */
interface GetMatchableUserDao {

	/** [loginAfter] 이후 로그인한 매칭 유저를 최근 로그인순으로 조회한다. (배치 대상이자 후보) */
	fun findMatchableUsers(loginAfter: LocalDateTime): List<MatchableUser>
}
