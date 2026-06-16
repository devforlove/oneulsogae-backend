package com.org.meeple.core.fixture

import com.org.meeple.common.match.MatchStatus
import com.org.meeple.common.match.MatchType
import com.org.meeple.common.user.Gender
import com.org.meeple.core.match.domain.Match
import com.org.meeple.core.match.domain.MatchMember
import com.org.meeple.core.match.domain.MatchMembers
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [Match] 도메인 모델 테스트 픽스처. 기본은 일일 배치(DAILY) 경로의 신규 소개(PROPOSED)이며, 참가자는 1:1 남/녀다.
 * 소개팅 신청/수락 코인 비용은 도메인 기본값(CoinUsageType)을 그대로 따른다.
 */
object MatchFixture {

	fun create(
		id: Long = 0,
		members: MatchMembers = membersOf(),
		introducedDate: LocalDate = LocalDate.of(2026, 1, 1),
		expiresAt: LocalDateTime = LocalDateTime.of(2026, 1, 2, 0, 0),
		matchType: MatchType = MatchType.DAILY,
		status: MatchStatus = MatchStatus.PROPOSED,
	): Match =
		Match(
			id = id,
			members = members,
			introducedDate = introducedDate,
			expiresAt = expiresAt,
			matchType = matchType,
			status = status,
		)

	/** 1:1 참가자(남/녀) 묶음. 각자의 수락 여부를 지정할 수 있다. */
	fun membersOf(
		maleUserId: Long = 1L,
		femaleUserId: Long = 2L,
		maleAccepted: Boolean? = null,
		femaleAccepted: Boolean? = null,
	): MatchMembers =
		MatchMembers(
			listOf(
				MatchMember(matchId = 0, userId = maleUserId, gender = Gender.MALE, accepted = maleAccepted),
				MatchMember(matchId = 0, userId = femaleUserId, gender = Gender.FEMALE, accepted = femaleAccepted),
			),
		)
}
