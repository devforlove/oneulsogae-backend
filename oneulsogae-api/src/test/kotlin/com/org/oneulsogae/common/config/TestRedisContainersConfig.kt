package com.org.oneulsogae.common.config

import com.redis.testcontainers.RedisContainer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean

/**
 * 통합테스트용 Redis Testcontainer 설정.
 *
 * Spring Boot의 `RedisContainerConnectionDetailsFactory`가 `com.redis.testcontainers.RedisContainer`를
 * 인식하므로, `@ServiceConnection`이 `spring.data.redis.*` 접속 정보를 컨텍스트에 자동 연결한다.
 * 컨테이너 빈은 `spring-boot-testcontainers`가 컨텍스트 기동 시 자동으로 start/stop 한다.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestRedisContainersConfig {

	@Bean
	@ServiceConnection
	fun redis(): RedisContainer =
		RedisContainer("redis:7.4")
}
