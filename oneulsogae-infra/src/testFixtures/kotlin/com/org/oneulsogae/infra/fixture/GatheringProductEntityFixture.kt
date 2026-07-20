package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.common.gathering.GatheringProductType
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.gathering.command.entity.GatheringProductEntity

/**
 * [GatheringProductEntity] 테스트 픽스처. 합리적 기본값을 주고, 필요한 값만 덮어쓴다.
 * (created_at은 저장 시 JPA Auditing이 채운다)
 */
object GatheringProductEntityFixture {

	fun create(
		gatheringId: Long = 1L,
		scheduleId: Long = 1L,
		gender: Gender = Gender.MALE,
		type: GatheringProductType = GatheringProductType.NORMAL,
		price: Int = 10000,
	): GatheringProductEntity =
		GatheringProductEntity(
			gatheringId = gatheringId,
			scheduleId = scheduleId,
			gender = gender,
			type = type,
			price = price,
		)

	/**
	 * 한 일정의 티어 세트를 만든다. 정가(남/녀)는 필수, 얼리버드 할인율·할인가는 선택.
	 * 얼리버드가는 정가 × (100 - 율) / 100 버림으로 계산한다. (도메인 생성 규칙과 동일)
	 */
	fun tierSet(
		gatheringId: Long,
		scheduleId: Long,
		maleFee: Int = 10000,
		femaleFee: Int = 8000,
		earlyBirdDiscountRate: Int? = null,
		discountMaleFee: Int? = null,
		discountFemaleFee: Int? = null,
	): List<GatheringProductEntity> {
		val products: MutableList<GatheringProductEntity> = mutableListOf(
			create(gatheringId, scheduleId, Gender.MALE, GatheringProductType.NORMAL, maleFee),
			create(gatheringId, scheduleId, Gender.FEMALE, GatheringProductType.NORMAL, femaleFee),
		)
		if (earlyBirdDiscountRate != null) {
			products += create(gatheringId, scheduleId, Gender.MALE, GatheringProductType.EARLY_BIRD, maleFee * (100 - earlyBirdDiscountRate) / 100)
			products += create(gatheringId, scheduleId, Gender.FEMALE, GatheringProductType.EARLY_BIRD, femaleFee * (100 - earlyBirdDiscountRate) / 100)
		}
		if (discountMaleFee != null) {
			products += create(gatheringId, scheduleId, Gender.MALE, GatheringProductType.DISCOUNT, discountMaleFee)
		}
		if (discountFemaleFee != null) {
			products += create(gatheringId, scheduleId, Gender.FEMALE, GatheringProductType.DISCOUNT, discountFemaleFee)
		}
		return products
	}
}
