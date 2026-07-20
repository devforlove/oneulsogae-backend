package com.org.oneulsogae.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * CORS 설정. 쿠키 인증(allowCredentials=true)을 쓰므로 와일드카드(*)가 아닌
 * 명시적 프론트엔드 오리진만 허용한다.
 */
@ConfigurationProperties(prefix = "app.cors")
data class CorsProperties(
	/** 허용할 프론트엔드 오리진 목록. (예: https://app.oneulsogae.com) */
	val allowedOrigins: List<String> = emptyList(),
)
