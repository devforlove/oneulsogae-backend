package com.org.meeple.core.user.command.application.port.out

/** 중복가입 차단: 다른 사용자가 이미 같은 DI로 본인확인(VERIFIED)했는지. */
interface ExistsIdentityByDiPort {
	fun existsVerifiedByDiOnOtherUser(di: String, userId: Long): Boolean
}
