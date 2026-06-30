package com.org.meeple.core.user.command.application.port.out

/** 사용자의 모든 인증 토큰(refresh token)을 폐기하는 아웃포트. */
interface RevokeUserTokensPort {

	/** [userId]의 모든 유효 refresh token을 폐기한다. */
	fun revokeAll(userId: Long)
}
