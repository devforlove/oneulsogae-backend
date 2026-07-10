package com.org.meeple.core.user.command.application.port.out

/** 중복가입 차단: 다른 사용자가 이미 같은 휴대폰 번호로 본인확인(VERIFIED)했는지. */
interface ExistsIdentityByPhoneNumberPort {
	fun existsVerifiedByPhoneNumberOnOtherUser(phoneNumber: String, userId: Long): Boolean
}
