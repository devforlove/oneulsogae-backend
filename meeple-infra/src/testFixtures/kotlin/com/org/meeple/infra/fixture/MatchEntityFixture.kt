package com.org.meeple.infra.fixture

import com.org.meeple.common.coin.CoinUsageType
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.MatchType
import com.org.meeple.infra.match.command.entity.MatchEntity
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [MatchEntity](매칭 헤더) 테스트 픽스처. 합리적 기본값을 주고, 필요한 값만 덮어쓴다.
 * 참가자(수락 여부 포함)는 [MatchMemberEntityFixture]로 따로 준비한다. (member_key는 참가자 조합과 일치시켜야 한다)
 * 기본은 PROPOSED 상태의 미응답 소개이며, 만료 시각은 생성 시점 + 1일이다.
 */
object MatchEntityFixture {

	fun create(
		memberKey: String = "1-2",
		introducedDate: LocalDate = LocalDate.now(),
		expiresAt: LocalDateTime = LocalDateTime.now().plusDays(1),
		matchType: MatchType = MatchType.DAILY,
		status: MatchStatus = MatchStatus.PROPOSED,
		dateInitAmount: Int = CoinUsageType.DATING_INIT.coinAmount,
		dateAcceptAmount: Int = CoinUsageType.DATING_ACCEPT.coinAmount,
	): MatchEntity =
		MatchEntity(
			memberKey = memberKey,
			introducedDate = introducedDate,
			expiresAt = expiresAt,
			status = status,
			matchType = matchType,
			dateInitAmount = dateInitAmount,
			dateAcceptAmount = dateAcceptAmount,
		)
}
