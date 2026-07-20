package com.org.oneulsogae.admin.gathering.command.domain

import com.org.oneulsogae.common.gathering.GatheringProductType
import com.org.oneulsogae.common.user.Gender

/**
 * 한 일정의 상품([GatheringProduct]) 일급 컬렉션.
 * 티어 구성 규칙을 캡슐화한다: 남/녀 × NORMAL은 필수, EARLY_BIRD는 할인율이 있을 때(가격 = 정가 × (100 - 율) / 100 버림),
 * DISCOUNT는 할인가가 있을 때 남/녀 행을 만든다.
 */
data class GatheringProducts(
	val values: List<GatheringProduct>,
) {

	/** 일정 저장 후 발급된 [scheduleId]를 모든 행에 채운 새 컬렉션을 돌려준다. */
	fun withScheduleId(scheduleId: Long): GatheringProducts =
		GatheringProducts(values.map { product: GatheringProduct -> product.copy(scheduleId = scheduleId) })

	companion object {

		/**
		 * [gatheringId] 모임 일정의 상품 목록을 만든다. 입력 검증(할인율 범위·세트 규칙)은
		 * [GatheringSchedule.create]가 먼저 수행하므로 여기서는 구성만 담당한다.
		 */
		fun create(
			gatheringId: Long,
			fee: GatheringFee,
			earlyBirdDiscountRate: Int?,
			discountFee: GatheringFee?,
		): GatheringProducts {
			val products: MutableList<GatheringProduct> = mutableListOf(
				product(gatheringId, Gender.MALE, GatheringProductType.NORMAL, fee.male),
				product(gatheringId, Gender.FEMALE, GatheringProductType.NORMAL, fee.female),
			)
			if (earlyBirdDiscountRate != null) {
				products += product(gatheringId, Gender.MALE, GatheringProductType.EARLY_BIRD, earlyBirdPrice(fee.male, earlyBirdDiscountRate))
				products += product(gatheringId, Gender.FEMALE, GatheringProductType.EARLY_BIRD, earlyBirdPrice(fee.female, earlyBirdDiscountRate))
			}
			if (discountFee != null) {
				products += product(gatheringId, Gender.MALE, GatheringProductType.DISCOUNT, discountFee.male)
				products += product(gatheringId, Gender.FEMALE, GatheringProductType.DISCOUNT, discountFee.female)
			}
			return GatheringProducts(products)
		}

		// 얼리버드가 = 정가 × (100 - 할인율) / 100 (정수 나눗셈 버림).
		private fun earlyBirdPrice(fee: Int, discountRate: Int): Int =
			fee * (100 - discountRate) / 100

		private fun product(gatheringId: Long, gender: Gender, type: GatheringProductType, price: Int): GatheringProduct =
			GatheringProduct(gatheringId = gatheringId, gender = gender, type = type, price = price)
	}
}
