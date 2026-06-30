package com.org.meeple.core.user.command.application.port.`in`

/** 탈퇴 유예가 지난 사용자 1명의 개인정보를 익명화(파기)하는 유스케이스. (배치가 사용자별로 호출) */
interface PurgeWithdrawnUserUseCase {

	fun purge(userId: Long)
}
