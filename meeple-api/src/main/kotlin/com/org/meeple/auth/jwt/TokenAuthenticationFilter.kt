package com.org.meeple.auth.jwt

import com.org.meeple.auth.PrincipalDetails
import com.org.meeple.infra.auth.session.ActiveSessionStore
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class TokenAuthenticationFilter(
	private val tokenProvider: TokenProvider,
	private val activeSessionStore: ActiveSessionStore,
) : OncePerRequestFilter() {

	override fun doFilterInternal(
		request: HttpServletRequest,
		response: HttpServletResponse,
		filterChain: FilterChain,
	) {
		resolveToken(request)
			?.takeIf { tokenProvider.validateToken(it) }
			?.let { token ->
				val authentication: Authentication = tokenProvider.getAuthentication(token)
				if (isActiveSession(token, authentication)) {
					SecurityContextHolder.getContext().authentication = authentication
				} else {
					// 토큰 자체는 유효하나 다른 기기/브라우저의 새 로그인에 밀려난 세션.
					// 여기서 직접 401을 쓰지 않고 표시만 남긴다. 보호 엔드포인트면 EntryPoint가 이 표시를 읽어
					// SESSION_TAKEN_OVER로 응답하고, permitAll 엔드포인트(/health 등)는 그대로 통과시킨다.
					request.setAttribute(SESSION_TAKEN_OVER_ATTRIBUTE, true)
				}
			}

		filterChain.doFilter(request, response)
	}

	/** 토큰의 session_id가 사용자의 현재 활성 세션과 일치하는지 확인한다. */
	private fun isActiveSession(token: String, authentication: Authentication): Boolean {
		val sessionId: String = tokenProvider.getSessionId(token) ?: return false
		val userId: Long = (authentication.principal as PrincipalDetails).id
		return activeSessionStore.isActive(userId, sessionId)
	}

	/** accessToken은 HttpOnly 쿠키를 우선 사용하고, 없으면 Bearer 헤더로 폴백한다. (모바일/서버 호출 대비) */
	private fun resolveToken(request: HttpServletRequest): String? =
		resolveFromCookie(request) ?: resolveFromHeader(request)

	private fun resolveFromCookie(request: HttpServletRequest): String? =
		request.cookies?.firstOrNull { it.name == TokenCookieFactory.ACCESS_TOKEN }?.value?.takeIf { it.isNotBlank() }

	private fun resolveFromHeader(request: HttpServletRequest): String? {
		val bearer: String = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return null
		return if (bearer.startsWith(BEARER_PREFIX)) bearer.substring(BEARER_PREFIX.length) else null
	}

	companion object {
		private const val BEARER_PREFIX = "Bearer "

		/**
		 * 토큰은 유효하나 단일 활성 세션에 밀려난 요청임을 표시하는 request attribute 키.
		 * 필터가 세팅하고, [com.org.meeple.auth.JsonAuthenticationEntryPoint]가 읽어 응답 코드를 가른다.
		 */
		const val SESSION_TAKEN_OVER_ATTRIBUTE = "auth.sessionTakenOver"
	}
}
