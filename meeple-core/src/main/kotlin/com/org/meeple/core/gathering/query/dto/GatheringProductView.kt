package com.org.meeple.core.gathering.query.dto

import com.org.meeple.common.gathering.GatheringProductType
import com.org.meeple.common.user.Gender

/** 일정의 성별·티어별 가격 상품 한 건(read model). 금액은 저장된 확정가다. [id]는 체크아웃·결제완료의 상품 식별자(적용 티어 행 기준)다. */
data class GatheringProductView(
	val id: Long,
	val gender: Gender,
	val type: GatheringProductType,
	val price: Int,
)
