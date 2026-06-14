package com.org.meeple.auth

import org.springframework.core.MethodParameter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/**
 * `@LoginUser AuthUser` 파라미터를 SecurityContext의 인증 주체에서 해석한다.
 * accessToken은 [com.org.meeple.auth.jwt.TokenAuthenticationFilter]가 이미 검증해
 * SecurityContext에 [PrincipalDetails]를 채워둔 상태다.
 */
@Component
class AuthUserArgumentResolver : HandlerMethodArgumentResolver {

	override fun supportsParameter(parameter: MethodParameter): Boolean =
		parameter.hasParameterAnnotation(LoginUser::class.java) &&
			AuthUser::class.java.isAssignableFrom(parameter.parameterType)

	override fun resolveArgument(
		parameter: MethodParameter,
		mavContainer: ModelAndViewContainer?,
		webRequest: NativeWebRequest,
		binderFactory: WebDataBinderFactory?,
	): Any? {
		val principal: Any? = SecurityContextHolder.getContext().authentication?.principal
		return (principal as? PrincipalDetails)?.let(AuthUser::from)
	}
}
