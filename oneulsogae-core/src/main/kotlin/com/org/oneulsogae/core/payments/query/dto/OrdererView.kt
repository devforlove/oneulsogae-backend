package com.org.oneulsogae.core.payments.query.dto

/**
 * 체크아웃 화면 주문자 정보 read model.
 * 실명은 최신 VERIFIED 본인인증, 이메일은 users, 휴대폰은 user_details에서 읽는다.
 * 본인인증·프로필 미완료 사용자는 각 필드가 null일 수 있다. (미비가 화면 진입을 막지 않는다)
 */
data class OrdererView(
	val name: String?,
	val email: String?,
	val phoneNumber: String?,
) {
	companion object {
		/** users 행을 찾지 못한 사용자에 대한 대체값. (모든 필드 null) */
		fun empty(): OrdererView = OrdererView(name = null, email = null, phoneNumber = null)
	}
}
