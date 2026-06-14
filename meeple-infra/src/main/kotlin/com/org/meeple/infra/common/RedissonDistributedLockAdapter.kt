package com.org.meeple.infra.common

import com.org.meeple.core.common.lock.DistributedLockPort
import org.redisson.api.RLock
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * [DistributedLockPort]의 Redisson 구현 어댑터.
 * 락 키 이름으로 [RLock]을 얻어 획득/해제한다. (RLock은 키 단위·재진입 락이라 같은 스레드면 unlock 시 동일 락을 다시 조회해도 안전하다)
 */
@Component
class RedissonDistributedLockAdapter(
	private val redissonClient: RedissonClient,
) : DistributedLockPort {

	override fun tryLock(key: String, waitTime: Long, leaseTime: Long, timeUnit: TimeUnit): Boolean =
		redissonClient.getLock(key).tryLock(waitTime, leaseTime, timeUnit)

	override fun unlock(key: String) {
		val lock: RLock = redissonClient.getLock(key)
		if (lock.isHeldByCurrentThread) {
			lock.unlock()
		}
	}
}
