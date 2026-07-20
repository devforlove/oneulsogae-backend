package com.org.oneulsogae.admin.gathering.command.domain

import com.org.oneulsogae.common.gathering.GatheringProductType
import com.org.oneulsogae.common.user.Gender

/**
 * 일정의 성별·티어별 가격 한 건(상품). 한 행 = 한 가격([gender] × [type])이며,
 * 얼리버드가도 조회 시점 계산 없이 생성 시점에 확정된 금액([price])으로 저장한다.
 * 일정 저장 전에는 [scheduleId]가 0이고, 저장 후 [GatheringProducts.withScheduleId]로 채운다.
 */
data class GatheringProduct(
	val id: Long = 0,
	val gatheringId: Long,
	val scheduleId: Long = 0,
	val gender: Gender,
	val type: GatheringProductType,
	val price: Int,
)
