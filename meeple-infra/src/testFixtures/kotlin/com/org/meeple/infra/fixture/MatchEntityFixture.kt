package com.org.meeple.infra.fixture

import com.org.meeple.common.coin.CoinUsageType
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.MatchType
import com.org.meeple.infra.match.entity.MatchEntity
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [MatchEntity] 테스트 픽스처. 합리적 기본값을 주고, 필요한 값만 덮어쓴다.
 * 기본은 PROPOSED 상태의 미응답 소개이며, 만료 시각은 생성 시점 + 1일이다.
 */
object MatchEntityFixture {

	fun create(
		maleUserId: Long = 1L,
		femaleUserId: Long = 2L,
		introducedDate: LocalDate = LocalDate.now(),
		expiresAt: LocalDateTime = LocalDateTime.now().plusDays(1),
		matchType: MatchType = MatchType.DAILY,
		maleAccepted: Boolean? = null,
		femaleAccepted: Boolean? = null,
		status: MatchStatus = MatchStatus.PROPOSED,
		dateInitAmount: Int = CoinUsageType.DATING_INIT.coinAmount,
		dateAcceptAmount: Int = CoinUsageType.DATING_ACCEPT.coinAmount,
	): MatchEntity =
		MatchEntity(
			maleUserId = maleUserId,
			femaleUserId = femaleUserId,
			introducedDate = introducedDate,
			expiresAt = expiresAt,
			maleAccepted = maleAccepted,
			femaleAccepted = femaleAccepted,
			status = status,
			matchType = matchType,
			dateInitAmount = dateInitAmount,
			dateAcceptAmount = dateAcceptAmount,
		)
}
