package com.org.meeple.core.gathering.query.dto

import com.org.meeple.common.gathering.GatheringProductType
import com.org.meeple.common.user.Gender

/** 일정의 성별·티어별 가격 상품 한 건(read model). 금액은 저장된 확정가다. */
data class GatheringProductView(
	val gender: Gender,
	val type: GatheringProductType,
	val price: Int,
)
