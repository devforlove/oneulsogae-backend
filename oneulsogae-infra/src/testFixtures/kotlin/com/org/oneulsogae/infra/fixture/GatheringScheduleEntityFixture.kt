package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.common.gathering.GatheringScheduleStatus
import com.org.oneulsogae.infra.gathering.command.entity.GatheringScheduleEntity
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
		maleCapacity: Int = 4,
		femaleCapacity: Int = 4,
		maleRemaining: Int = maleCapacity,
		femaleRemaining: Int = femaleCapacity,
		earlyBirdCapacity: Int? = null,
		earlyBirdRemaining: Int? = earlyBirdCapacity,
		status: GatheringScheduleStatus = GatheringScheduleStatus.SCHEDULED,
	): GatheringScheduleEntity =
		GatheringScheduleEntity(
			gatheringId = gatheringId,
			startAt = startAt,
			endAt = endAt,
			maleCapacity = maleCapacity,
			femaleCapacity = femaleCapacity,
			maleRemaining = maleRemaining,
			femaleRemaining = femaleRemaining,
			earlyBirdCapacity = earlyBirdCapacity,
			earlyBirdRemaining = earlyBirdRemaining,
			status = status,
		)
}
