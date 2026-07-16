package com.org.meeple.infra.fixture

import com.org.meeple.common.gathering.GatheringMemberStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.gathering.command.entity.GatheringMemberEntity

/**
 * [GatheringMemberEntity] 테스트 픽스처. 합리적 기본값을 주고, 필요한 값만 덮어쓴다.
 * (created_at은 저장 시 JPA Auditing이 채운다)
 */
object GatheringMemberEntityFixture {

	fun create(
		gatheringId: Long = 1L,
		scheduleId: Long = 1L,
		userId: Long = 1L,
		gender: Gender = Gender.MALE,
		status: GatheringMemberStatus = GatheringMemberStatus.PENDING,
		earlyBirdApplied: Boolean = false,
	): GatheringMemberEntity =
		GatheringMemberEntity(
			gatheringId = gatheringId,
			scheduleId = scheduleId,
			userId = userId,
			gender = gender,
			status = status,
			earlyBirdApplied = earlyBirdApplied,
		)
}
