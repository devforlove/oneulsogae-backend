package com.org.meeple.common.config

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

/**
 * 통합테스트용 WireMock 설정. 외부 HTTP 의존성을 스텁한다.
 *
 * Kotest는 JUnit5 `@RegisterExtension` 모델과 호환되지 않으므로, WireMock 서버를 Spring 빈으로 둔다.
 * 컨텍스트 기동/종료에 맞춰 `start`/`stop` 되고, 컨텍스트 캐싱으로 스펙 간 재사용된다.
 * (테스트 사이 스텁/요청 기록 초기화는 [com.org.meeple.common.integration.AbstractIntegrationSupport]의 `afterEach`에서 수행)
 */
@TestConfiguration(proxyBeanMethods = false)
class TestWireMockConfig {

	@Bean(initMethod = "start", destroyMethod = "stop")
	fun wireMockServer(): WireMockServer =
		WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort())
}
