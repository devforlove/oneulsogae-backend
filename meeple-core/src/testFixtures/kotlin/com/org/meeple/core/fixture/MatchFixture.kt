package com.org.meeple.core.fixture

import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.MatchType
import com.org.meeple.core.match.domain.Match
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [Match] 도메인 모델 테스트 픽스처. 기본은 일일 배치(DAILY) 경로의 신규 소개(PROPOSED)다.
 * 소개팅 신청/수락 코인 비용은 도메인 기본값(CoinUsageType)을 그대로 따른다.
 */
object MatchFixture {

	fun create(
		id: Long = 0,
		maleUserId: Long = 1L,
		femaleUserId: Long = 2L,
		introducedDate: LocalDate = LocalDate.of(2026, 1, 1),
		expiresAt: LocalDateTime = LocalDateTime.of(2026, 1, 2, 0, 0),
		matchType: MatchType = MatchType.DAILY,
		maleAccepted: Boolean? = null,
		femaleAccepted: Boolean? = null,
		status: MatchStatus = MatchStatus.PROPOSED,
	): Match =
		Match(
			id = id,
			maleUserId = maleUserId,
			femaleUserId = femaleUserId,
			introducedDate = introducedDate,
			expiresAt = expiresAt,
			matchType = matchType,
			maleAccepted = maleAccepted,
			femaleAccepted = femaleAccepted,
			status = status,
		)
}
