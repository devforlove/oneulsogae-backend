package com.org.oneulsogae.auth.jwt

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * accessToken/refreshToken을 HttpOnly 쿠키로 빌드한다.
 * 토큰 평문이 JS(브라우저 스크립트)에 노출되지 않도록 항상 HttpOnly로 내려준다.
 */
@Component
class TokenCookieFactory(
	private val properties: AuthCookieProperties,
	@Value("\${jwt.expiration_time}")
	private val accessExpireMillis: Long,
	@Value("\${jwt.refresh_expiration_time}")
	private val refreshExpireMillis: Long,
) {

	fun accessTokenCookie(token: String): ResponseCookie =
		build(ACCESS_TOKEN, token, ACCESS_PATH, Duration.ofMillis(accessExpireMillis))

	fun refreshTokenCookie(token: String): ResponseCookie =
		build(REFRESH_TOKEN, token, REFRESH_PATH, Duration.ofMillis(refreshExpireMillis))

	/** 로그아웃/만료 시 즉시 삭제되는 빈 쿠키. (maxAge=0) */
	fun expiredAccessTokenCookie(): ResponseCookie =
		build(ACCESS_TOKEN, "", ACCESS_PATH, Duration.ZERO)

	fun expiredRefreshTokenCookie(): ResponseCookie =
		build(REFRESH_TOKEN, "", REFRESH_PATH, Duration.ZERO)

	private fun build(name: String, value: String, path: String, maxAge: Duration): ResponseCookie =
		ResponseCookie.from(name, value)
			.httpOnly(true) // JS가 못 읽게 한다.
			.secure(properties.secure) // https 요청인 경우에만 쿠키가 실린다.
			.sameSite(properties.sameSite) // csrf 방지. 같은 도메인에 대한 요청만 쿠키가 실린다.
			.path(path)
			.maxAge(maxAge)
			.apply { if (properties.domain.isNotBlank()) domain(properties.domain) }
			.build()

	companion object {
		const val ACCESS_TOKEN = "accessToken"
		const val REFRESH_TOKEN = "refreshToken"

		/** accessToken은 모든 API 요청에 필요하므로 루트 경로. */
		private const val ACCESS_PATH = "/"

		/** refreshToken은 재발급(/auth/v1/refresh)·로그아웃(/auth/v1/logout)에서만 필요하므로 두 경로를 함께 감싸는 최소 prefix로 좁힌다. */
		private const val REFRESH_PATH = "/auth/v1"
	}
}
