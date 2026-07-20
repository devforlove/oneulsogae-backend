package com.org.oneulsogae.auth.jwt

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class TokenAuthenticationFilter(
	private val tokenProvider: TokenProvider,
) : OncePerRequestFilter() {

	override fun doFilterInternal(
		request: HttpServletRequest,
		response: HttpServletResponse,
		filterChain: FilterChain,
	) {
		resolveToken(request)
			?.takeIf { tokenProvider.validateToken(it) }
			?.let { token ->
				SecurityContextHolder.getContext().authentication = tokenProvider.getAuthentication(token)
			}

		filterChain.doFilter(request, response)
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
	}
}
