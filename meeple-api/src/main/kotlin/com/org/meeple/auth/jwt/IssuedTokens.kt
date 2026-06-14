package com.org.meeple.auth.jwt

/** 발급된 access/refresh 토큰 쌍. */
data class IssuedTokens(
	val accessToken: String,
	val refreshToken: String,
)

/** refresh token 검증/회전 실패. (만료·위조·재사용 감지 등) */
class InvalidRefreshTokenException(message: String) : RuntimeException(message)
