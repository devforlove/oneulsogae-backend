package com.org.meeple.core.match.query.dao

import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.query.dto.MatchWithPartner
import java.time.LocalDateTime

/**
 * 매칭 + 상대 프로필 조인 조회 dao(query out-port 인터페이스).
 * 조회 결과 read model([MatchWithPartner])을 반환하며, 실제 QueryDSL 구현은 infra 레이어가 담당한다.
 */
interface MatchWithPartnerDao {

	/**
	 * 사용자가 참가한 매칭 + 상대 프로필을 조인 조회한다. (만료된 소개는 [now] 기준으로 제외)
	 * 1:1이라 상대 참가자는 정확히 한 명이다. ([gender]는 더 이상 컬럼 선택에 쓰지 않아 무시한다)
	 */
	fun findAllWithPartnerByUserId(userId: Long, gender: Gender, now: LocalDateTime): List<MatchWithPartner>
}
