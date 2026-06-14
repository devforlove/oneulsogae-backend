package com.org.meeple.auth.jwt

import com.org.meeple.auth.PrincipalDetails
import com.org.meeple.infra.auth.entity.RefreshTokenEntity
import com.org.meeple.infra.auth.repository.RefreshTokenRepository
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * refresh token의 발급·회전·폐기를 담당한다.
 * 모든 refresh token은 jti 단위로 저장소에 추적되며, 회전 시 이전 토큰은 즉시 폐기된다.
 */
@Service
class RefreshTokenService(
	private val tokenProvider: TokenProvider,
	private val refreshTokenRepository: RefreshTokenRepository,
) {

	/** 로그인 성공 시 access/refresh 토큰을 발급하고 refresh를 저장한다. */
	@Transactional
	fun issue(authentication: Authentication): IssuedTokens {
		val accessToken: String = tokenProvider.generateAccessToken(authentication)
		val refreshToken: String = createAndStore(authentication)
		return IssuedTokens(accessToken, refreshToken)
	}

	/**
	 * refresh token을 회전한다. 기존 토큰을 폐기하고 새 access/refresh를 발급한다.
	 * 이미 폐기된 토큰이 다시 들어오면 탈취로 간주해 해당 사용자의 모든 토큰을 폐기한다.
	 */
	@Transactional
	fun rotate(refreshToken: String): IssuedTokens {
		if (!tokenProvider.validateToken(refreshToken)) {
			throw InvalidRefreshTokenException("유효하지 않거나 만료된 refresh token")
		}

		val tokenId: String = tokenProvider.getTokenId(refreshToken)
			?: throw InvalidRefreshTokenException("jti가 없는 refresh token")

		val stored: RefreshTokenEntity = refreshTokenRepository.findByTokenId(tokenId)
			?: throw InvalidRefreshTokenException("추적되지 않는 refresh token")

		if (!stored.isActive) {
			// 이미 폐기/회전된 토큰의 재사용 → 탈취 의심, 사용자 전체 세션 무효화
			refreshTokenRepository.revokeAllByUserId(stored.userId)
			throw InvalidRefreshTokenException("재사용이 감지된 refresh token")
		}

		// 기존 refreshToken 폐기
		stored.revoke()
		refreshTokenRepository.save(stored)

		val authentication: Authentication = tokenProvider.getAuthentication(refreshToken)
		val accessToken: String = tokenProvider.generateAccessToken(authentication)
		val newRefreshToken: String = createAndStore(authentication)
		return IssuedTokens(accessToken, newRefreshToken)
	}

	/** 로그아웃: 전달된 refresh token을 폐기한다. (유효하지 않으면 조용히 무시) */
	@Transactional
	fun revoke(refreshToken: String?) {
		if (refreshToken.isNullOrBlank() || !tokenProvider.validateToken(refreshToken)) return
		val tokenId: String = tokenProvider.getTokenId(refreshToken) ?: return
		refreshTokenRepository.findByTokenId(tokenId)?.let {
			it.revoke()
			refreshTokenRepository.save(it)
		}
	}

	private fun createAndStore(authentication: Authentication): String {
		val tokenId: String = UUID.randomUUID().toString()
		val refreshToken: String = tokenProvider.generateRefreshToken(authentication, tokenId)
		val userId: Long = (authentication.principal as PrincipalDetails).id
		refreshTokenRepository.save(
			RefreshTokenEntity(
				tokenId = tokenId,
				userId = userId,
				expiresAt = tokenProvider.getExpiration(refreshToken),
			),
		)
		return refreshToken
	}
}
