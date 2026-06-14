package com.org.meeple.infra.config

import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 분산 락(@DistributedLock)이 사용할 [RedissonClient] 빈을 등록한다.
 * 매칭 풀 적재용 Lettuce(StringRedisTemplate)와는 별개의 클라이언트로,
 * 같은 Redis 서버(spring.data.redis.host/port)에 단일 서버 모드로 접속한다.
 */
@Configuration
class RedissonConfig(
	@param:Value("\${spring.data.redis.host:localhost}") private val host: String,
	@param:Value("\${spring.data.redis.port:6379}") private val port: Int,
) {

	/** 애플리케이션 종료 시 커넥션을 정리하도록 destroyMethod로 shutdown을 건다. */
	@Bean(destroyMethod = "shutdown")
	fun redissonClient(): RedissonClient {
		val config: Config = Config()
		config.useSingleServer()
			.setAddress("redis://$host:$port")
		return Redisson.create(config)
	}
}
