package com.org.meeple.auth.oauth

import com.org.meeple.auth.jwt.IssuedTokens
import com.org.meeple.auth.jwt.RefreshTokenService
import com.org.meeple.auth.jwt.TokenCookieFactory
import com.org.meeple.common.user.Role
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class OAuth2SuccessHandler(
	private val refreshTokenService: RefreshTokenService,
	private val tokenCookieFactory: TokenCookieFactory,
	private val oAuth2Properties: OAuth2Properties,
) : SimpleUrlAuthenticationSuccessHandler() {

	override fun onAuthenticationSuccess(
		request: HttpServletRequest,
		response: HttpServletResponse,
		authentication: Authentication,
	) {
		val tokens: IssuedTokens = refreshTokenService.issue(authentication)

		// 토큰을 URL이 아닌 HttpOnly 쿠키로 전달한다. (브라우저 히스토리/로그/Referer 유출 방지)
		response.addHeader(HttpHeaders.SET_COOKIE, tokenCookieFactory.accessTokenCookie(tokens.accessToken).toString())
		response.addHeader(HttpHeaders.SET_COOKIE, tokenCookieFactory.refreshTokenCookie(tokens.refreshToken).toString())

		redirectStrategy.sendRedirect(request, response, redirectUriFor(authentication))
	}

	/** ROLE_ADMIN 유저는 어드민 프론트로, 그 외는 앱 프론트로 리다이렉트한다. */
	private fun redirectUriFor(authentication: Authentication): String =
		if (authentication.authorities.any { authority: GrantedAuthority -> authority.authority == Role.ADMIN.authority() }) {
			oAuth2Properties.adminRedirectUri
		} else {
			oAuth2Properties.redirectUri
		}
}
