package com.org.meeple.auth.oauth

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.oauth2")
data class OAuth2Properties(
	/** 로그인 성공 후 토큰을 붙여 리다이렉트할 프론트엔드 URI. */
	val redirectUri: String,
	/** 로그인 실패 시 리다이렉트할 프론트엔드 base. 뒤에 `/error?code={코드}`가 붙는다. (예: `{base}/error?code=USER-009`) */
	val failureRedirectUri: String,
	/**
	 * 어드민 프론트에서 시작한(`/oauth2/authorization/{provider}?origin=admin`) 로그인 성공 시
	 * 리다이렉트할 어드민 프론트엔드 URI. ROLE_ADMIN이 아니면 이 URI에 `?error=ACCESS_DENIED`가 붙는다.
	 */
	val adminRedirectUri: String,
	/** 모바일 앱에서 시작한(`?origin=mobile`) 로그인 성공 시 리다이렉트할 커스텀 스킴 딥링크. (예: meeplemobile://auth) */
	val mobileRedirectUri: String,
)
