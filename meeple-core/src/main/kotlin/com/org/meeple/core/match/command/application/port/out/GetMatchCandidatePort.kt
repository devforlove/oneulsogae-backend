package com.org.meeple.core.match.command.application.port.out

import com.org.meeple.common.user.Gender
import java.time.LocalDateTime

/**
 * 매칭 후보 조회 아웃포트.
 * 소개 가능한 상대 후보(반대 성별 · 같은 활동 권역 · 정식 가입 사용자)를 찾는다.
 */
interface GetMatchCandidatePort {

	/**
	 * 주어진 성별([gender]) · 같은 활동 권역([regionCode]) 중
	 * 마지막 로그인([loginAfter] 이후)이 유효한 정식 가입(ACTIVE) 사용자 1명의 userId를 반환한다.
	 * 후보가 없으면 null. (MVP: 무작위 선정)
	 */
	fun findOneCandidate(gender: Gender, regionCode: Int, loginAfter: LocalDateTime): Long?
}
