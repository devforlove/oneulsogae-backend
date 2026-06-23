package com.org.meeple.common.config

import com.org.meeple.scheduler.match.command.application.port.out.RegionShuffler
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/**
 * 통합 테스트에서 RegionShuffler를 항등(순서 유지)으로 고정해 결정적으로 만드는 설정.
 * [AbstractIntegrationSupport]에 등록돼 모든 통합 테스트가 단일 컨텍스트를 공유한다.
 * 실제 셔플 로직은 RandomRegionShufflerTest(유닛)에서 검증한다.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestRegionShufflerConfig {

	@Bean
	@Primary
	fun deterministicRegionShuffler(): RegionShuffler = RegionShuffler { regionIds: List<Long> -> regionIds }
}
