package com.org.meeple.scheduler.match.query.dto

import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.MaritalStatus
import java.time.LocalDateTime

/**
 * 매칭 배치 대상 한 건. 다음 커서 산출에 필요한 [lastLoginAt]과
 * 매칭 판단에 필요한 프로필([gender]/[maritalStatus]/[regionId])을 함께 담는다.
 */
data class MatchBatchTarget(
	val userId: Long,
	val lastLoginAt: LocalDateTime,
	val gender: Gender? = null,
	val maritalStatus: MaritalStatus? = null,
	val regionId: Long? = null,
)
