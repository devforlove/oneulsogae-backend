package com.org.meeple.auth.oauth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * OAuth2 로그인 시작 요청(`/oauth2/authorization/{provider}`)에서 출처 파라미터(origin)를 읽어
 * [LoginOriginCookieFactory]의 출처 쿠키로 보존한다. 공급자로 갔다 돌아온 콜백에는 쿼리가 남지 않으므로
 * 쿠키로 왕복을 버텨 [OAuth2SuccessHandler]가 시작 프론트를 알 수 있게 한다.
 * origin=admin이 아니면 이전 어드민 로그인 시도의 잔여 쿠키를 지워 오판을 막는다.
 */
@Component
class LoginOriginCookieFilter(
	private val loginOriginCookieFactory: LoginOriginCookieFactory,
) : OncePerRequestFilter() {

	override fun doFilterInternal(
		request: HttpServletRequest,
		response: HttpServletResponse,
		filterChain: FilterChain,
	) {
		if (request.requestURI.startsWith(AUTHORIZATION_PATH_PREFIX)) {
			val cookie: ResponseCookie =
				if (request.getParameter(LoginOriginCookieFactory.ORIGIN_PARAM) == LoginOriginCookieFactory.ADMIN_ORIGIN) {
					loginOriginCookieFactory.adminOriginCookie()
				} else {
					loginOriginCookieFactory.expiredOriginCookie()
				}
			response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
		}
		filterChain.doFilter(request, response)
	}

	companion object {
		/** Spring Security OAuth2 로그인 시작(인가 요청) 경로 prefix. */
		private const val AUTHORIZATION_PATH_PREFIX: String = "/oauth2/authorization/"
	}
}
