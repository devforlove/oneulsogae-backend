package com.org.meeple.core.user.command.application.port.`in`

import com.org.meeple.core.user.command.domain.User

/**
 * 사용자 가입 인포트(유스케이스).
 * OAuth 인증 사용자가 처음이면 신규 저장하고, 이미 있으면 기존 사용자를 그대로 반환한다.
 */
interface RegisterUserUseCase {

	fun registerIfAbsent(
		provider: String,
		providerId: String,
		email: String?,
		profileImageUrl: String?,
	): User
}
