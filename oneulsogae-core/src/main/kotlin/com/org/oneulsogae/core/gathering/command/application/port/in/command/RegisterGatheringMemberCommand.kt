package com.org.oneulsogae.core.gathering.command.application.port.`in`.command

import com.org.oneulsogae.common.gathering.GatheringProductType
import com.org.oneulsogae.common.user.Gender

/**
 * 참가 접수 명령. [gender]는 호출자(payments)가 본인 프로필에서 확정해 넘긴다.
 * [type]은 결제한 상품(productId)의 가격 티어로, 접수 시 이 티어의 저장가로 결제액을 확정한다
 * (얼리버드 티어인데 소진됐으면 접수를 거부해 체크아웃 금액과 실결제 금액이 어긋나지 않게 한다).
 */
data class RegisterGatheringMemberCommand(
	val gatheringId: Long,
	val scheduleId: Long,
	val userId: Long,
	val gender: Gender,
	val type: GatheringProductType,
)
