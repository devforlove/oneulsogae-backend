package com.org.meeple.infra.config

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.redisson.config.SingleServerConfig
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 분산 락(@DistributedLock)이 사용할 [RedissonClient] 빈을 등록한다.
 *
 * 접속 정보는 Spring Data Redis와 동일한 [DataRedisConnectionDetails]에서 얻는다. 이렇게 하면
 * 운영(`spring.data.redis.*` 프로퍼티)·테스트(`@ServiceConnection` Testcontainers) 어느 환경이든
 * 매칭 풀 적재용 Lettuce(StringRedisTemplate)와 **같은 Redis**에 접속한다.
 * (이전에는 `spring.data.redis.host`를 직접 읽어, `@ServiceConnection`이 프로퍼티를 채우지 않는
 *  테스트에서 Redisson만 기본값 localhost로 붙는 문제가 있었다.)
 */
@Configuration
class RedissonConfig(
	private val connectionDetails: DataRedisConnectionDetails,
) {

	/** 애플리케이션 종료 시 커넥션을 정리하도록 destroyMethod로 shutdown을 건다. */
	@Bean(destroyMethod = "shutdown")
	fun redissonClient(): RedissonClient {
		val standalone: DataRedisConnectionDetails.Standalone = requireNotNull(connectionDetails.standalone) {
			"Redisson은 단일 서버(standalone) Redis만 지원한다(cluster/sentinel 미지원)."
		}
		val config: Config = Config()
		val singleServer: SingleServerConfig = config.useSingleServer()
			.setAddress("redis://${standalone.host}:${standalone.port}")
			.setDatabase(standalone.database)
		connectionDetails.username?.let { username: String -> singleServer.setUsername(username) }
		connectionDetails.password?.let { password: String -> singleServer.setPassword(password) }
		return Redisson.create(config)
	}
}
