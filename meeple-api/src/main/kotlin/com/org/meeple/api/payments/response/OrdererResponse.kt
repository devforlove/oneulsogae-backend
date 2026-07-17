package com.org.meeple.api.payments.response

import com.org.meeple.core.payments.query.dto.OrdererView

/** 체크아웃 주문자 정보 응답. userId는 요청자 본인 id다. 본인인증·프로필 미완료 사용자는 나머지 필드가 null일 수 있다. */
data class OrdererResponse(
	val userId: Long,
	val name: String?,
	val email: String?,
	val phoneNumber: String?,
) {
	companion object {
		fun of(userId: Long, view: OrdererView): OrdererResponse =
			OrdererResponse(
				userId = userId,
				name = view.name,
				email = view.email,
				phoneNumber = view.phoneNumber,
			)
	}
}
