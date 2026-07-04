package com.org.meeple.infra.fixture

import com.org.meeple.common.coin.CoinUsageType
import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.TeamMatchType
import com.org.meeple.infra.teammatch.command.entity.TeamMatchEntity
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [TeamMatchEntity](팀 매칭 헤더) 테스트 픽스처. 합리적 기본값을 주고, 필요한 값만 덮어쓴다.
 * 기본은 PROPOSED 상태의 팀 매칭이며, 만료 시각은 생성 시점 + 1일이다.
 */
object TeamMatchEntityFixture {

	fun create(
		memberKey: String = "1-2",
		introducedDate: LocalDate = LocalDate.now(),
		expiresAt: LocalDateTime = LocalDateTime.now().plusDays(1),
		matchType: TeamMatchType = TeamMatchType.DAILY,
		status: MatchStatus = MatchStatus.PROPOSED,
		dateInitAmount: Int = CoinUsageType.MEETING_INIT.coinAmount,
		dateAcceptAmount: Int = CoinUsageType.MEETING_ACCEPT.coinAmount,
	): TeamMatchEntity = TeamMatchEntity(memberKey, introducedDate, expiresAt, status, matchType, dateInitAmount, dateAcceptAmount)
}
