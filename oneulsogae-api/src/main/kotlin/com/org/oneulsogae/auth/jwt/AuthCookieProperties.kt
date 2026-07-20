package com.org.oneulsogae.auth.jwt

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 인증 토큰 쿠키 설정. 환경(local/prod)에 따라 달라지는 값만 외부화한다.
 * - local: domain 비움(host-only), secure=false (http)
 * - prod: domain=".oneulsogae.com"(서브도메인 공유), secure=true (https)
 */
@ConfigurationProperties(prefix = "app.auth.cookie")
data class AuthCookieProperties(
	/** 쿠키 도메인. 비어 있으면 host-only로 설정한다. (예: ".oneulsogae.com") */
	val domain: String = "",
	/** HTTPS 전용 여부. 운영에서는 반드시 true. */
	val secure: Boolean = false,
	/** SameSite 정책. 같은 서브도메인 구성에서는 Lax 권장. */
	val sameSite: String = "Lax",
)
