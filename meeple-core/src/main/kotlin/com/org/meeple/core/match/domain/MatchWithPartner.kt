package com.org.meeple.core.match.domain

import com.org.meeple.core.user.domain.UserDetail

/**
 * 매칭과 (조회한 사용자 기준의) 상대방 프로필을 함께 담는 조회 결과(read model).
 * [userId]는 이 결과를 조회한 사용자이고, [partner]는 그 반대편 참가자의 [UserDetail]이다.
 */
data class MatchWithPartner(
	val userId: Long,
	val match: Match,
	val partner: UserDetail,
) {
	/** 조회 사용자가 이 매칭에 관심을 보냈는지 여부. */
	val hasUserInterest: Boolean
		get() = match.hasUserInterest(userId)

	/** 상대방이 이 매칭에 관심을 보냈는지 여부. */
	val hasPartnerInterest: Boolean
		get() = match.hasPartnerInterest(userId)
}
