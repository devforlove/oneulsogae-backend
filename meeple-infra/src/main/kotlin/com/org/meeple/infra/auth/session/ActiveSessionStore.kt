package com.org.meeple.infra.auth.session

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 사용자당 "현재 활성 세션"을 Redis에 기록해 단일 활성 세션(마지막 로그인 우선)을 강제한다.
 *
 * access token은 stateless라 새 로그인 후에도 만료 전까지 계속 유효하다. 이 저장소가 매 요청에서 대조되는
 * stateful 마커 역할을 해, 새 로그인이 마커를 덮어쓰는 순간 이전 세션의 토큰은 다음 요청에서 무효가 된다.
 *
 * 키 형식: auth:active-session:{userId} → 현재 활성 sessionId.
 * TTL은 refresh token 수명에 맞추고, 토큰 회전(rotate)마다 [renew]로 미뤄(slide) 활동 중에는 유지되게 한다.
 *
 * **장애 정책(fail-open).** 단일 활성 세션은 stateless JWT(서명·만료 검증은 그대로 동작) 위에 얹은 정책일 뿐이므로,
 * Redis 장애가 인증 전체를 막는 단일 장애점이 되지 않도록 Redis 접근 실패([DataAccessException])는 삼키고
 * "정책 미적용"으로 진행한다. 즉 장애 동안에는 단일 세션이 일시적으로 강제되지 않을 뿐 서비스는 계속 동작한다.
 * (정책상 fail-closed가 필요하면 이 처리를 제거해 예외를 그대로 전파시키면 된다)
 */
@Component
class ActiveSessionStore(
	private val stringRedisTemplate: StringRedisTemplate,
	@Value("\${jwt.refresh_expiration_time}")
	private val ttlMillis: Long,
) {

	/** 로그인 시 현재 활성 세션을 [sessionId]로 덮어쓴다. (이전 세션은 다음 요청에서 무효가 된다) */
	fun activate(userId: Long, sessionId: String) {
		runCatchingRedis("activate", userId) {
			stringRedisTemplate.opsForValue().set(keyOf(userId), sessionId, ttl())
		}
	}

	/**
	 * 토큰의 [sessionId]가 현재 활성 세션과 일치하는지 확인한다. (매 요청 인증 필터에서 사용)
	 * Redis 장애 시 fail-open: true(허용)를 반환해, 인증 경로가 Redis에 묶이지 않게 한다.
	 */
	fun isActive(userId: Long, sessionId: String): Boolean =
		runCatchingRedis("isActive", userId, onError = true) {
			stringRedisTemplate.opsForValue().get(keyOf(userId)) == sessionId
		}

	/**
	 * 토큰 회전 시 호출한다. [sessionId]가 현재 활성 세션과 같을 때만 TTL을 미루고 true를 반환한다.
	 * 다른 세션에 밀려난(덮어써진) 경우 false를 반환하므로, 호출 측은 회전을 거부해야 한다.
	 * (값은 그대로 두고 TTL만 갱신해, 검사~갱신 사이 새 로그인이 끼어들어도 그 세션을 덮어쓰지 않는다)
	 * Redis 장애 시 fail-open: true(허용)를 반환해 토큰 재발급이 막히지 않게 한다.
	 */
	fun renew(userId: Long, sessionId: String): Boolean =
		runCatchingRedis("renew", userId, onError = true) {
			if (stringRedisTemplate.opsForValue().get(keyOf(userId)) != sessionId) {
				return@runCatchingRedis false
			}
			stringRedisTemplate.expire(keyOf(userId), ttl())
			true
		}

	/**
	 * 로그아웃 시 호출한다. 현재 활성 세션이 [sessionId]일 때만 마커를 제거한다.
	 * (이미 다른 세션에 밀려난 세션의 로그아웃이 활성 세션을 죽이지 않도록 소유권을 확인한다)
	 */
	fun clear(userId: Long, sessionId: String) {
		runCatchingRedis("clear", userId) {
			if (stringRedisTemplate.opsForValue().get(keyOf(userId)) == sessionId) {
				stringRedisTemplate.delete(keyOf(userId))
			}
		}
	}

	// Redis 접근 실패를 삼키고 fail-open 기본값을 돌려준다. (예외를 인증 경로로 전파하지 않는다)
	private inline fun <T> runCatchingRedis(op: String, userId: Long, onError: T, block: () -> T): T =
		try {
			block()
		} catch (e: DataAccessException) {
			log.warn("active session {} 실패(Redis 장애) - 단일 세션 미적용으로 진행 userId={}", op, userId, e)
			onError
		}

	private inline fun runCatchingRedis(op: String, userId: Long, block: () -> Unit) {
		try {
			block()
		} catch (e: DataAccessException) {
			log.warn("active session {} 실패(Redis 장애) - 단일 세션 미적용으로 진행 userId={}", op, userId, e)
		}
	}

	private fun ttl(): Duration = Duration.ofMillis(ttlMillis)

	private fun keyOf(userId: Long): String = "$KEY_PREFIX$userId"

	companion object {
		private val log: Logger = LoggerFactory.getLogger(ActiveSessionStore::class.java)

		private const val KEY_PREFIX: String = "auth:active-session:"
	}
}
