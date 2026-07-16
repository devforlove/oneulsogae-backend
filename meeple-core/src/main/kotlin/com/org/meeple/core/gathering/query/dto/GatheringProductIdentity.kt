package com.org.meeple.core.gathering.query.dto

import com.org.meeple.common.user.Gender

/**
 * 상품 한 건의 식별 정보 read model. productId로 (모임, 일정, 성별)을 해석한다.
 * 체크아웃 조회·결제완료 접수가 productId 하나로 상품을 지정할 때 쓴다.
 * 타입은 식별에 쓰지 않는다(어느 티어 행이든 같은 모임·일정·성별로 해석되고, 실결제가는 서버 티어 규칙으로 확정).
 */
data class GatheringProductIdentity(
	val productId: Long,
	val gatheringId: Long,
	val scheduleId: Long,
	val gender: Gender,
)
