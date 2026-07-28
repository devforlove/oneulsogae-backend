package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.infra.user.command.entity.ReferralRewardGrantEntity
import java.time.LocalDateTime

/**
 * [ReferralRewardGrantEntity] 테스트 픽스처. 추천 보상이 지급된 이력 한 건이다.
 * referred_di_hash는 유니크라 한 테스트에서 여러 건을 만들면 값을 달리한다.
 */
object ReferralRewardGrantEntityFixture {

	fun create(
		referredDiHash: String = "di-hash-fix",
		referrerUserId: Long = 1L,
		referredUserId: Long = 2L,
		coinAmount: Int = 50,
		grantedAt: LocalDateTime = LocalDateTime.of(2026, 7, 9, 12, 0),
	): ReferralRewardGrantEntity =
		ReferralRewardGrantEntity(
			referredDiHash = referredDiHash,
			referrerUserId = referrerUserId,
			referredUserId = referredUserId,
			coinAmount = coinAmount,
			grantedAt = grantedAt,
		)
}
