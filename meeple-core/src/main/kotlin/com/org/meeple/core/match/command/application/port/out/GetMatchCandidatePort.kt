package com.org.meeple.core.match.command.application.port.out

import com.org.meeple.common.user.Gender
import java.time.LocalDateTime

/**
 * 매칭 후보 조회 아웃포트.
 * 요청자에게 소개할 상대(반대 성별 · 가까운 지역 순 · 재소개 이력 없음) 후보를 찾는다.
 */
interface GetMatchCandidatePort {

	/**
	 * 요청자([requesterId])의 지역([regionId])에서 가까운 지역 순으로,
	 * 반대 성별([gender]) · 최근 로그인([loginAfter] 이후) · [requesterId]와 재소개 이력이 없는 후보 1명의 userId를 반환한다.
	 * 후보가 없으면 null. (가장 가까운 지역의 후보를 우선하며, 같은 지역 내에서는 최근 로그인 우선)
	 */
	fun findOneCandidate(requesterId: Long, gender: Gender, regionId: Long, loginAfter: LocalDateTime): Long?
}
