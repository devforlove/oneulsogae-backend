package com.org.meeple.auth.oauth

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder

/**
 * OAuth2 로그인 실패 시 서버 에러 페이지 대신 프론트엔드로 리다이렉트하는 핸들러.
 * 실패 원인(이메일 누락/중복 등)은 [OAuth2FailureHandler] 경로로 오기 위해 [CustomOAuth2UserService]에서
 * [OAuth2AuthenticationException]으로 감싸지므로, 그 errorCode를 `?error=`로 붙여 프론트가 분기·안내하게 한다.
 */
@Component
class OAuth2FailureHandler(
	private val oAuth2Properties: OAuth2Properties,
) : SimpleUrlAuthenticationFailureHandler() {

	override fun onAuthenticationFailure(
		request: HttpServletRequest,
		response: HttpServletResponse,
		exception: AuthenticationException,
	) {
		val errorCode: String = (exception as? OAuth2AuthenticationException)?.error?.errorCode ?: LOGIN_FAILED
		// 프론트 base 뒤에 /error 경로 + code 쿼리로 붙인다. (예: http://localhost:3000/error?code=USER-009)
		val target: String = UriComponentsBuilder.fromUriString(oAuth2Properties.failureRedirectUri)
			.path("/error")
			.queryParam("code", errorCode)
			.build()
			.toUriString()
		redirectStrategy.sendRedirect(request, response, target)
	}

	companion object {
		/** errorCode를 식별할 수 없는 일반 인증 실패(취소·토큰 교환 실패 등)에 쓰는 기본 코드. */
		private const val LOGIN_FAILED: String = "LOGIN_FAILED"
	}
}
