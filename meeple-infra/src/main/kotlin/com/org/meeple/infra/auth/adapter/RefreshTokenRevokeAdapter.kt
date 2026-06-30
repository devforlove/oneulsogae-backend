package com.org.meeple.infra.auth.adapter

import com.org.meeple.core.user.command.application.port.out.RevokeUserTokensPort
import com.org.meeple.infra.auth.repository.RefreshTokenRepository
import org.springframework.stereotype.Component

/** [RevokeUserTokensPort] 구현. 사용자의 모든 유효 refresh token을 폐기한다. */
@Component
class RefreshTokenRevokeAdapter(
	private val refreshTokenRepository: RefreshTokenRepository,
) : RevokeUserTokensPort {

	override fun revokeAll(userId: Long) {
		refreshTokenRepository.revokeAllByUserId(userId)
	}
}
