package com.org.meeple.config

import com.org.meeple.auth.JsonAuthenticationEntryPoint
import com.org.meeple.auth.jwt.TokenAuthenticationFilter
import com.org.meeple.auth.oauth.CustomOAuth2UserService
import com.org.meeple.auth.oauth.LoginOriginCookieFilter
import com.org.meeple.auth.oauth.OAuth2FailureHandler
import com.org.meeple.auth.oauth.OAuth2SuccessHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.builders.WebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter
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
		loginOriginCookieFilter: LoginOriginCookieFilter,
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
					// 모바일 앱용 인증 엔드포인트(일회용 코드 교환·갱신·로그아웃)는 access token 없이 호출된다.
					.requestMatchers("/auth/v1/mobile/**").permitAll()
					// WebSocket 핸드셰이크(SockJS 하위 경로 포함)는 토큰을 못 싣으므로 열어둔다.
					// 실제 인증은 STOMP CONNECT 프레임에서 AuthChannelInterceptor가 수행한다.
					.requestMatchers("/ws/chat/**").permitAll()
					// 비로그인 사용자도 고객센터 문의를 접수할 수 있도록 연다. (토큰 있으면 컨트롤러가 회원 ID로 귀속)
					.requestMatchers("/inquiries/v1").permitAll()
					// 오프라인(비인증 공개) 모임 목록·상세 조회는 토큰 없이 접근할 수 있다.
					.requestMatchers(HttpMethod.GET, "/offline/v1/gatherings", "/offline/v1/gatherings/*").permitAll()
					// 소개 이미지 공개 프록시(presigned 302 리다이렉트)도 비로그인 접근을 허용한다.
					.requestMatchers(HttpMethod.GET, "/images/**").permitAll()
					// 어드민 API는 role=ADMIN 유저(토큰의 ROLE_ADMIN 권한)만 접근한다. (어드민도 같은 OAuth2+JWT 체계를 쓴다)
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
			// 로그인 시작 요청의 출처(origin=admin)를 쿠키로 보존한다. (인가 요청 리다이렉트가 응답을 끝내기 전에 실려야 한다)
			.addFilterBefore(loginOriginCookieFilter, OAuth2AuthorizationRequestRedirectFilter::class.java)
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
