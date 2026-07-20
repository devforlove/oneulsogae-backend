package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.common.gathering.GatheringStatus
import com.org.oneulsogae.common.gathering.GatheringType
import com.org.oneulsogae.infra.gathering.command.entity.GatheringEntity

/**
 * [GatheringEntity] 테스트 픽스처. 합리적 기본값을 주고, 필요한 값만 덮어쓴다.
 * 저장 날짜(created_at)는 저장 시 JPA Auditing이 채운다.
 */
object GatheringEntityFixture {

	fun create(
		type: GatheringType = GatheringType.PARTY,
		userId: Long? = null,
		title: String = "모임 제목",
		description: String? = "모임 소개",
		imageKey: String? = "gatherings/fixture.png",
		region: String = "서울 강남구",
		minParticipants: Int = 2,
		maxParticipants: Int = 4,
		status: GatheringStatus = GatheringStatus.RECRUITING,
	): GatheringEntity =
		GatheringEntity(
			type = type,
			userId = userId,
			title = title,
			description = description,
			imageKey = imageKey,
			region = region,
			minParticipants = minParticipants,
			maxParticipants = maxParticipants,
			status = status,
		)
}
