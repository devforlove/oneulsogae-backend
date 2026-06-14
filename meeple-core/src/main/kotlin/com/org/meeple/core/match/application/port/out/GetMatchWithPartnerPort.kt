package com.org.meeple.core.match.application.port.out

import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.domain.MatchWithPartner
import java.time.LocalDateTime

/**
 * 매칭 + 상대 프로필 조인 조회 아웃포트. (동적 컬럼·조인이 필요해 QueryDSL로 구현)
 * 단순 단건/존재 조회는 [GetMatchPort]가 담당한다.
 * 실제 구현은 infra 레이어의 [com.org.meeple.infra.match.adapter.MatchQueryAdapter]가 담당한다.
 */
interface GetMatchWithPartnerPort {

	/**
	 * 해당 사용자가 참가한 매칭을 상대방 프로필과 조인해 함께 반환한다. (1+N 방지)
	 * 사용자는 자신의 성별([gender]) 컬럼에만 등장하므로 그쪽만 조회한다. (where절 OR/CASE 불필요)
	 * 만료된 소개([now] 기준 만료 시각이 지난 것)는 제외한다.
	 * [MatchWithPartner.partner]는 조회 사용자의 반대편 참가자 프로필이다.
	 */
	fun findAllWithPartnerByUserId(userId: Long, gender: Gender, now: LocalDateTime): List<MatchWithPartner>
}
