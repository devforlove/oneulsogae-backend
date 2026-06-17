package com.org.meeple.auth

import tools.jackson.databind.ObjectMapper
import com.org.meeple.auth.jwt.TokenAuthenticationFilter
import com.org.meeple.auth.jwt.TokenCookieFactory
import com.org.meeple.core.common.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets

/**
 * 인증이 필요한 요청인데 인증 정보가 없거나(accessToken 부재) 유효하지 않을 때(만료/위조) 호출된다.
 *
 * TokenAuthenticationFilter는 유효한 토큰일 때만 SecurityContext를 채우고, 그 외에는 통과시킨다.
 * 이후 인가 단계에서 인증이 비어 있는 보호 엔드포인트에 대해 Spring Security가 이 EntryPoint를 호출하므로,
 * 유효하지 않은 토큰은 비즈니스 로직까지 진행되지 못하고 여기서 일관된 ApiResponse(JSON) 401로 차단된다.
 *
 * 토큰은 유효하나 다른 기기/브라우저의 새 로그인에 밀려난(단일 활성 세션) 경우, 필터가
 * [TokenAuthenticationFilter.SESSION_TAKEN_OVER_ATTRIBUTE]를 세팅해 둔다. 그 표시가 있으면
 * 일반 인증 실패(AUTH-001)와 구분해 SESSION_TAKEN_OVER(AUTH-002)로 응답하고, 죽은 인증 쿠키를 비워
 * 프론트가 재로그인 플로우로 보낼 수 있게 한다.
 */
@Component
class JsonAuthenticationEntryPoint(
	private val objectMapper: ObjectMapper,
	private val tokenCookieFactory: TokenCookieFactory,
) : AuthenticationEntryPoint {

	override fun commence(
		request: HttpServletRequest,
		response: HttpServletResponse,
		authException: AuthenticationException,
	) {
		val takenOver: Boolean = request.getAttribute(TokenAuthenticationFilter.SESSION_TAKEN_OVER_ATTRIBUTE) == true
		val errorCode: AuthErrorCode =
			if (takenOver) AuthErrorCode.SESSION_TAKEN_OVER else AuthErrorCode.AUTHENTICATION_REQUIRED

		if (takenOver) {
			// 밀려난 세션의 죽은 토큰 쿠키를 즉시 삭제한다.
			response.addHeader(HttpHeaders.SET_COOKIE, tokenCookieFactory.expiredAccessTokenCookie().toString())
			response.addHeader(HttpHeaders.SET_COOKIE, tokenCookieFactory.expiredRefreshTokenCookie().toString())
		}

		response.status = errorCode.status.value()
		response.contentType = MediaType.APPLICATION_JSON_VALUE
		response.characterEncoding = StandardCharsets.UTF_8.name()
		response.writer.write(objectMapper.writeValueAsString(ApiResponse.error(errorCode)))
	}
}
