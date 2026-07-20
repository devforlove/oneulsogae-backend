package com.org.oneulsogae.common.gathering

/** 모임 일정 상품([com.org.oneulsogae.infra.gathering.command.entity.GatheringProductEntity])의 가격 타입. */
enum class GatheringProductType(val description: String) {

	/** 정가. 모든 일정이 남/녀 각 1행씩 가진다. */
	NORMAL("정가"),

	/** 얼리버드가. 얼리버드 선착순(스케줄의 early_bird_remaining)이 남아있을 때 적용된다. */
	EARLY_BIRD("얼리버드가"),

	/** 할인가. 얼리버드 소진 후 적용된다. */
	DISCOUNT("할인가"),
}
