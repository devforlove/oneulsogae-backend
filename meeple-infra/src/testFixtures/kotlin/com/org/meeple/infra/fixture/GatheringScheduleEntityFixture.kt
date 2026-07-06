package com.org.meeple.infra.fixture

import com.org.meeple.common.gathering.GatheringScheduleStatus
import com.org.meeple.infra.gathering.command.entity.GatheringScheduleEntity
import java.time.LocalDateTime

/**
 * [GatheringScheduleEntity] 테스트 픽스처. 합리적 기본값을 주고, 필요한 값만 덮어쓴다.
 * (created_at은 저장 시 JPA Auditing이 채운다)
 */
object GatheringScheduleEntityFixture {

	fun create(
		gatheringId: Long = 1L,
		startAt: LocalDateTime = LocalDateTime.of(2999, 12, 31, 18, 0, 0),
		endAt: LocalDateTime? = LocalDateTime.of(2999, 12, 31, 20, 0, 0),
		maleFee: Int = 10000,
		femaleFee: Int = 8000,
		earlyBirdMaleFee: Int? = null,
		earlyBirdFemaleFee: Int? = null,
		earlyBirdCapacity: Int? = null,
		earlyBirdRemaining: Int? = earlyBirdCapacity,
		discountMaleFee: Int? = null,
		discountFemaleFee: Int? = null,
		status: GatheringScheduleStatus = GatheringScheduleStatus.SCHEDULED,
	): GatheringScheduleEntity =
		GatheringScheduleEntity(
			gatheringId = gatheringId,
			startAt = startAt,
			endAt = endAt,
			maleFee = maleFee,
			femaleFee = femaleFee,
			earlyBirdMaleFee = earlyBirdMaleFee,
			earlyBirdFemaleFee = earlyBirdFemaleFee,
			earlyBirdCapacity = earlyBirdCapacity,
			earlyBirdRemaining = earlyBirdRemaining,
			discountMaleFee = discountMaleFee,
			discountFemaleFee = discountFemaleFee,
			status = status,
		)
}
