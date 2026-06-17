package com.org.meeple.auth.jwt

/** 발급된 access/refresh 토큰 쌍. */
data class IssuedTokens(
	val accessToken: String,
	val refreshToken: String,
)

/** refresh token 검증/회전 실패. (만료·위조·재사용 감지 등) */
class InvalidRefreshTokenException(message: String) : RuntimeException(message)

/** 다른 기기/브라우저의 새 로그인에 밀려나(단일 활성 세션) 더 이상 유효하지 않은 세션. */
class SessionTakenOverException(message: String) : RuntimeException(message)
