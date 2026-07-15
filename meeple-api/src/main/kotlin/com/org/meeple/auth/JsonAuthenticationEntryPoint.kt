package com.org.meeple.auth

import tools.jackson.databind.ObjectMapper
import com.org.meeple.core.common.response.ApiResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
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
 */
@Component
class JsonAuthenticationEntryPoint(
	private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint {

	override fun commence(
		request: HttpServletRequest,
		response: HttpServletResponse,
		authException: AuthenticationException,
	) {
		val errorCode: AuthErrorCode = AuthErrorCode.AUTHENTICATION_REQUIRED

		response.status = errorCode.status.value()
		response.contentType = MediaType.APPLICATION_JSON_VALUE
		response.characterEncoding = StandardCharsets.UTF_8.name()
		response.writer.write(objectMapper.writeValueAsString(ApiResponse.error(errorCode)))
	}
}
