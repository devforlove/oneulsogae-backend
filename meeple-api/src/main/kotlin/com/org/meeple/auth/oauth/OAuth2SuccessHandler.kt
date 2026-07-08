package com.org.meeple.auth.oauth

import com.org.meeple.auth.jwt.IssuedTokens
import com.org.meeple.auth.jwt.RefreshTokenService
import com.org.meeple.auth.jwt.TokenCookieFactory
import com.org.meeple.common.user.Role
import com.org.meeple.infra.auth.code.MobileAuthCodeStore
import com.org.meeple.infra.auth.code.StoredTokens
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.web.util.UriComponentsBuilder
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component

/**
 * OAuth2 로그인 성공 처리. 토큰을 HttpOnly 쿠키로 심고 로그인을 시작한 프론트로 리다이렉트한다.
 * - 앱 프론트에서 시작(출처 쿠키 없음): 역할과 무관하게 앱 프론트로. (어드민도 일반 서비스 이용 가능)
 * - 어드민 프론트에서 시작(출처 쿠키 admin) + ROLE_ADMIN: 어드민 프론트로.
 * - 어드민 프론트에서 시작 + ROLE_ADMIN 아님: 토큰을 발급하지 않고 어드민 프론트에 `?error=ACCESS_DENIED`로 복귀.
 * - 모바일 앱에서 시작(출처 쿠키 mobile): 토큰을 쿠키에 심지 않고, 일회용 code로 교환해 커스텀 스킴 딥링크로 복귀.
 */
@Component
class OAuth2SuccessHandler(
	private val refreshTokenService: RefreshTokenService,
	private val tokenCookieFactory: TokenCookieFactory,
	private val loginOriginCookieFactory: LoginOriginCookieFactory,
	private val mobileAuthCodeStore: MobileAuthCodeStore,
	private val oAuth2Properties: OAuth2Properties,
) : SimpleUrlAuthenticationSuccessHandler() {

	override fun onAuthenticationSuccess(
		request: HttpServletRequest,
		response: HttpServletResponse,
		authentication: Authentication,
	) {
		val adminOrigin: Boolean = isAdminOrigin(request)
		// 출처 쿠키는 이번 로그인 1회용이므로 결과와 무관하게 제거한다.
		response.addHeader(HttpHeaders.SET_COOKIE, loginOriginCookieFactory.expiredOriginCookie().toString())

		// 모바일 앱에서 시작한 로그인이면 쿠키 대신 일회용 code로 교환해 딥링크로 돌려보낸다.
		if (isMobileOrigin(request)) {
			val tokens: IssuedTokens = refreshTokenService.issue(authentication)
			val code: String = mobileAuthCodeStore.issue(StoredTokens(tokens.accessToken, tokens.refreshToken))
			val redirectUri: String = UriComponentsBuilder.fromUriString(oAuth2Properties.mobileRedirectUri)
				.queryParam("code", code)
				.build()
				.toUriString()
			redirectStrategy.sendRedirect(request, response, redirectUri)
			return
		}

		// 어드민 프론트에서 시작한 로그인인데 어드민 권한이 없으면 토큰 없이 에러 표식만 붙여 돌려보낸다.
		if (adminOrigin && !isAdmin(authentication)) {
			redirectStrategy.sendRedirect(request, response, adminAccessDeniedUri())
			return
		}

		val tokens: IssuedTokens = refreshTokenService.issue(authentication)

		// 토큰을 URL이 아닌 HttpOnly 쿠키로 전달한다. (브라우저 히스토리/로그/Referer 유출 방지)
		response.addHeader(HttpHeaders.SET_COOKIE, tokenCookieFactory.accessTokenCookie(tokens.accessToken).toString())
		response.addHeader(HttpHeaders.SET_COOKIE, tokenCookieFactory.refreshTokenCookie(tokens.refreshToken).toString())

		val redirectUri: String = if (adminOrigin) oAuth2Properties.adminRedirectUri else oAuth2Properties.redirectUri
		redirectStrategy.sendRedirect(request, response, redirectUri)
	}

	/** 이번 로그인이 어드민 프론트에서 시작됐는지 출처 쿠키로 판별한다. */
	private fun isAdminOrigin(request: HttpServletRequest): Boolean =
		request.cookies?.any { cookie: Cookie ->
			cookie.name == LoginOriginCookieFactory.LOGIN_ORIGIN && cookie.value == LoginOriginCookieFactory.ADMIN_ORIGIN
		} == true

	/** 이번 로그인이 모바일 앱에서 시작됐는지 출처 쿠키로 판별한다. */
	private fun isMobileOrigin(request: HttpServletRequest): Boolean =
		request.cookies?.any { cookie: Cookie ->
			cookie.name == LoginOriginCookieFactory.LOGIN_ORIGIN && cookie.value == LoginOriginCookieFactory.MOBILE_ORIGIN
		} == true

	private fun isAdmin(authentication: Authentication): Boolean =
		authentication.authorities.any { authority: GrantedAuthority -> authority.authority == Role.ADMIN.authority() }

	private fun adminAccessDeniedUri(): String =
		UriComponentsBuilder.fromUriString(oAuth2Properties.adminRedirectUri)
			.queryParam("error", "ACCESS_DENIED")
			.build()
			.toUriString()
}
