package com.org.oneulsogae.infra.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializer
import org.springframework.data.redis.serializer.StringRedisSerializer
import tools.jackson.databind.ObjectMapper

/**
 * Spring Data Redis(Lettuce) 설정.
 *
 * 접속 정보(`spring.data.redis.host`/`port`)로 Spring Boot가 자동 구성한 [RedisConnectionFactory]를 그대로 주입받아,
 * 키는 사람이 읽을 수 있는 문자열([StringRedisSerializer]), 값은 JSON([GenericJacksonJsonRedisSerializer])으로
 * 직렬화하는 [RedisTemplate] 빈을 등록한다.
 *
 * 값 직렬화기는 앱의 기본 JSON 스택과 동일한 Jackson 3(`tools.jackson`) [ObjectMapper] 빈을 주입해 사용하므로,
 * 컨트롤러 등에서 쓰는 직렬화 설정(코틀린 모듈 등)과 Redis 저장 형식이 일관된다.
 * (자동 구성되는 [org.springframework.data.redis.core.StringRedisTemplate]은 문자열 전용이라 그대로 두고,
 *  객체 값 저장이 필요한 경우 이 템플릿을 쓴다.)
 *
 * 분산 락용 [org.redisson.api.RedissonClient]는 [RedissonConfig]에서 같은 Redis 서버에 별도 클라이언트로 접속한다.
 */
@Configuration
class RedisConfig {

	@Bean
	fun redisTemplate(
		connectionFactory: RedisConnectionFactory,
		objectMapper: ObjectMapper,
	): RedisTemplate<String, Any> {
		val template: RedisTemplate<String, Any> = RedisTemplate()
        template.connectionFactory = connectionFactory

		val keySerializer: RedisSerializer<String> = StringRedisSerializer()
		val valueSerializer: RedisSerializer<Any> = GenericJacksonJsonRedisSerializer(objectMapper)

		template.keySerializer = keySerializer
		template.hashKeySerializer = keySerializer
		template.valueSerializer = valueSerializer
		template.hashValueSerializer = valueSerializer
		template.afterPropertiesSet()
		return template
	}
}
