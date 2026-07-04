package com.org.meeple.auth.oauth

import com.org.meeple.auth.jwt.AuthCookieProperties
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * OAuth2 로그인을 시작한 프론트 출처(app/admin)를 공급자 왕복 동안 보존하는 1회용 쿠키 빌더.
 * 어드민 프론트는 `/oauth2/authorization/{provider}?origin=admin`으로 로그인을 시작하고,
 * [LoginOriginCookieFilter]가 이 쿠키를 심으면 [OAuth2SuccessHandler]가 읽어 리다이렉트를 가른 뒤 제거한다.
 * (공급자에서 돌아오는 최상위 리다이렉트에 실려야 하므로 SameSite=Lax 전제)
 */
@Component
class LoginOriginCookieFactory(
	private val properties: AuthCookieProperties,
) {

	/** 어드민 프론트에서 시작한 로그인 표식 쿠키. 공급자 왕복 시간만 버티면 되므로 수명을 짧게 둔다. */
	fun adminOriginCookie(): ResponseCookie = build(ADMIN_ORIGIN, Duration.ofMinutes(5))

	/** 출처 쿠키 즉시 삭제용 빈 쿠키. (maxAge=0) */
	fun expiredOriginCookie(): ResponseCookie = build("", Duration.ZERO)

	private fun build(value: String, maxAge: Duration): ResponseCookie =
		ResponseCookie.from(LOGIN_ORIGIN, value)
			.httpOnly(true)
			.secure(properties.secure)
			.sameSite(properties.sameSite)
			.path("/")
			.maxAge(maxAge)
			.build()

	companion object {
		/** 출처 쿠키 이름. 백엔드 호스트에만 필요해 host-only(도메인 미지정)로 둔다. */
		const val LOGIN_ORIGIN: String = "loginOrigin"

		/** 어드민 프론트 출처 값. */
		const val ADMIN_ORIGIN: String = "admin"

		/** 로그인 시작 요청에서 출처를 받는 쿼리 파라미터 이름. */
		const val ORIGIN_PARAM: String = "origin"
	}
}
