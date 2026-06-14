package com.org.meeple.core.common.lock

import java.util.concurrent.TimeUnit

/**
 * 분산 락 획득/해제 out-port.
 * core는 락 메커니즘(Redis 등)에 직접 의존하지 않고, 이 포트로 락 원시(primitive)만 추상화한다.
 * 획득→실행→해제 오케스트레이션은 [DistributedLockAspect]가 담당하고, 실제 구현은 infra 어댑터(Redisson)가 제공한다.
 */
interface DistributedLockPort {

	/**
	 * 락 획득을 시도한다. [waitTime] 안에 얻으면 true, 못 얻으면 false.
	 * 얻은 락은 [leaseTime]이 지나면 자동 해제된다.
	 */
	fun tryLock(key: String, waitTime: Long, leaseTime: Long, timeUnit: TimeUnit): Boolean

	/** 현재 스레드가 보유 중인 경우에만 락을 해제한다. (이미 만료된 락 해제 시 예외를 막는다) */
	fun unlock(key: String)
}
