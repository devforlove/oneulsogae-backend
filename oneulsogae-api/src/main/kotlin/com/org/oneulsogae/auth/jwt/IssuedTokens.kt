package com.org.oneulsogae.auth.jwt

/** 발급된 access/refresh 토큰 쌍. */
data class IssuedTokens(
	val accessToken: String,
	val refreshToken: String,
)
