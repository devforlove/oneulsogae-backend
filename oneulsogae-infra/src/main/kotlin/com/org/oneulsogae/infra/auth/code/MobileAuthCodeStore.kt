package com.org.oneulsogae.infra.auth.code

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

/**
 * 모바일 OAuth 성공 시 발급한 토큰을 잠깐 보관하고, 앱이 딥링크로 받은 일회용 code로 교환하게 한다.
 * code는 단일 사용(consume 시 즉시 삭제) + 짧은 TTL로 유출/재사용 창을 최소화한다.
 * 키: auth:mobile-code:{code} → "{accessToken}\n{refreshToken}".
 */
@Component
class MobileAuthCodeStore(
	private val stringRedisTemplate: StringRedisTemplate,
) {

	fun issue(tokens: StoredTokens): String {
		val code: String = UUID.randomUUID().toString()
		stringRedisTemplate.opsForValue().set(keyOf(code), encode(tokens), TTL)
		return code
	}

	fun consume(code: String): StoredTokens? {
		val raw: String = stringRedisTemplate.opsForValue().getAndDelete(keyOf(code)) ?: return null
		return decode(raw)
	}

	private fun encode(tokens: StoredTokens): String = "${tokens.accessToken}\n${tokens.refreshToken}"

	private fun decode(raw: String): StoredTokens {
		val separator: Int = raw.indexOf('\n')
		return StoredTokens(raw.substring(0, separator), raw.substring(separator + 1))
	}

	private fun keyOf(code: String): String = "$KEY_PREFIX$code"

	companion object {
		private const val KEY_PREFIX: String = "auth:mobile-code:"
		private val TTL: Duration = Duration.ofSeconds(60)
	}
}
