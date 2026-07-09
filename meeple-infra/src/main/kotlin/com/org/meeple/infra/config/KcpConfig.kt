package com.org.meeple.infra.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class KcpConfig {

	@Bean
	fun kcpRestClient(properties: KcpProperties): RestClient =
		RestClient.builder()
			.baseUrl(properties.baseUrl)
			.build()
}
