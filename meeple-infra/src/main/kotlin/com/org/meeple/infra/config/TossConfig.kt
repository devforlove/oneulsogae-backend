package com.org.meeple.infra.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient
import java.nio.charset.StandardCharsets
import java.util.Base64

@Configuration
class TossConfig {

	/**
	 * 토스페이먼츠 API 호출용 RestClient.
	 * 인증은 시크릿키를 사용자명으로, 비밀번호는 빈 값으로 하는 Basic 인증이다: base64("{secretKey}:").
	 */
	@Bean
	fun tossRestClient(properties: TossProperties): RestClient {
		val basicToken: String = Base64.getEncoder()
			.encodeToString("${properties.secretKey}:".toByteArray(StandardCharsets.UTF_8))
		return RestClient.builder()
			.baseUrl(properties.baseUrl)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic $basicToken")
			.build()
	}
}
