package com.org.meeple.config

import com.org.meeple.auth.JsonAuthenticationEntryPoint
import com.org.meeple.auth.jwt.TokenAuthenticationFilter
import com.org.meeple.auth.oauth.CustomOAuth2UserService
import com.org.meeple.auth.oauth.OAuth2FailureHandler
import com.org.meeple.auth.oauth.OAuth2SuccessHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.builders.WebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class SecurityConfig(
	private val oAuth2UserService: CustomOAuth2UserService,
	private val oAuth2SuccessHandler: OAuth2SuccessHandler,
	private val oAuth2FailureHandler: OAuth2FailureHandler,
	private val corsProperties: CorsProperties,
) {

	@Bean
	fun webSecurityCustomizer(): WebSecurityCustomizer =
		WebSecurityCustomizer { web: WebSecurity ->
			web.ignoring().requestMatchers("/error", "/favicon.ico")
		}

	@Bean
	fun securityFilterChain(
		http: HttpSecurity,
		tokenAuthenticationFilter: TokenAuthenticationFilter,
		jsonAuthenticationEntryPoint: JsonAuthenticationEntryPoint,
	): SecurityFilterChain =
		http
			.httpBasic { it.disable() }
			.formLogin { it.disable() }
			.cors { it.configurationSource(corsConfigurationSource()) }
			// CSRF는 SameSite 쿠키 + 명시적 오리진 CORS로 방어한다. (상태 변경은 교차 오리진 preflight 필요)
			.csrf { it.disable() }
			.logout { it.disable() }
			.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
			.authorizeHttpRequests { request ->
				request
					.requestMatchers("/", "/health").permitAll()
					.requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
						.requestMatchers("/oauth2/**", "/login/**").permitAll()
					// 토큰 재발급/로그아웃은 access token이 만료된 상태에서도 호출되므로 허용한다.
					.requestMatchers("/auth/v1/refresh", "/auth/v1/logout").permitAll()
					// WebSocket 핸드셰이크(SockJS 하위 경로 포함)는 토큰을 못 싣으므로 열어둔다.
					// 실제 인증은 STOMP CONNECT 프레임에서 AuthChannelInterceptor가 수행한다.
					.requestMatchers("/ws/chat/**").permitAll()
					// 관리자 전용(매칭 배치 수동 실행 등)은 ROLE_ADMIN만 허용한다.
					.requestMatchers("/admin/**").hasRole("ADMIN")
					.anyRequest().authenticated()
			}
			// 인증이 필요한 요청인데 토큰이 없거나 유효하지 않으면(만료/위조) 401 JSON으로 차단한다.
			.exceptionHandling { it.authenticationEntryPoint(jsonAuthenticationEntryPoint) }
			.oauth2Login { oauth2 ->
				oauth2
					.userInfoEndpoint { it.userService(oAuth2UserService) }
					.successHandler(oAuth2SuccessHandler)
					.failureHandler(oAuth2FailureHandler)
			}
			// JWT 인가 필터: 인증된 요청의 토큰을 검증해 SecurityContext를 채운다.
			.addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
			.build()

	private fun corsConfigurationSource(): CorsConfigurationSource {
		val config: CorsConfiguration = CorsConfiguration().apply {
			allowedOrigins = corsProperties.allowedOrigins
			allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
			allowedHeaders = listOf("*")
			// 쿠키 기반 인증을 위해 자격증명 전송 허용. (프론트는 fetch credentials: 'include')
			allowCredentials = true
		}
		return UrlBasedCorsConfigurationSource().apply {
			registerCorsConfiguration("/**", config)
		}
	}
}
