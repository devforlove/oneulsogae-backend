package com.org.oneulsogae.core.gathering.query.dto

import com.org.oneulsogae.common.gathering.GatheringProductType
import com.org.oneulsogae.common.user.Gender

/**
 * 상품 한 건의 식별 정보 read model. productId로 (모임, 일정, 성별, 가격 티어)를 해석한다.
 * 체크아웃 조회·결제완료 접수가 productId 하나로 상품을 지정할 때 쓴다.
 * [type]은 결제완료 접수 시 결제액을 그 티어의 저장가로 확정하고, 얼리버드 소진 여부를 판정하는 데 쓴다.
 */
data class GatheringProductIdentity(
	val productId: Long,
	val gatheringId: Long,
	val scheduleId: Long,
	val gender: Gender,
	val type: GatheringProductType,
)
