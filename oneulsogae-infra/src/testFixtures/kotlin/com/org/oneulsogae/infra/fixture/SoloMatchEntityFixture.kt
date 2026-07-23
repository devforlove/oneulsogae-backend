package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.common.coin.CoinUsageType
import com.org.oneulsogae.common.match.MatchStatus
import com.org.oneulsogae.common.match.SoloMatchType
import com.org.oneulsogae.infra.solomatch.command.entity.SoloMatchEntity
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [SoloMatchEntity](매칭 헤더) 테스트 픽스처. 합리적 기본값을 주고, 필요한 값만 덮어쓴다.
 * 참가자(수락 여부 포함)는 [SoloMatchMemberEntityFixture]로 따로 준비한다. (member_key는 참가자 조합과 일치시켜야 한다)
 * 기본은 PROPOSED 상태의 미응답 소개이며, 만료 시각은 생성 시점 + 1일이다.
 */
object SoloMatchEntityFixture {

	fun create(
		memberKey: String = "1-2",
		introducedDate: LocalDate = LocalDate.now(),
		expiresAt: LocalDateTime = LocalDateTime.now().plusDays(1),
		matchType: SoloMatchType = SoloMatchType.DAILY,
		status: MatchStatus = MatchStatus.PROPOSED,
		dateInitAmount: Int = CoinUsageType.DATING_INIT.coinAmount(null),
		dateAcceptAmount: Int = CoinUsageType.DATING_ACCEPT.coinAmount(null),
	): SoloMatchEntity =
		SoloMatchEntity(
			memberKey = memberKey,
			introducedDate = introducedDate,
			expiresAt = expiresAt,
			status = status,
			matchType = matchType,
			dateInitAmount = dateInitAmount,
			dateAcceptAmount = dateAcceptAmount,
		)
}
