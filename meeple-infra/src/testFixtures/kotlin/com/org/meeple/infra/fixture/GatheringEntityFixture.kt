package com.org.meeple.infra.fixture

import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.common.gathering.GatheringType
import com.org.meeple.infra.gathering.command.entity.GatheringEntity
import java.time.LocalDateTime

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
		gatheringAt: LocalDateTime = LocalDateTime.of(2999, 12, 31, 18, 0, 0),
		capacity: Int = 4,
		maleFee: Int = 10000,
		femaleFee: Int = 8000,
		earlyBirdMaleFee: Int? = null,
		earlyBirdFemaleFee: Int? = null,
		discountMaleFee: Int? = null,
		discountFemaleFee: Int? = null,
		status: GatheringStatus = GatheringStatus.RECRUITING,
	): GatheringEntity =
		GatheringEntity(
			type = type,
			userId = userId,
			title = title,
			description = description,
			imageKey = imageKey,
			region = region,
			gatheringAt = gatheringAt,
			capacity = capacity,
			maleFee = maleFee,
			femaleFee = femaleFee,
			earlyBirdMaleFee = earlyBirdMaleFee,
			earlyBirdFemaleFee = earlyBirdFemaleFee,
			discountMaleFee = discountMaleFee,
			discountFemaleFee = discountFemaleFee,
			status = status,
		)
}
