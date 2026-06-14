package com.org.meeple.core.common.lock

import java.util.concurrent.TimeUnit

/**
 * 메서드 실행을 분산 락으로 감싸는 어노테이션.
 * [DistributedLockAspect]가 이 어노테이션이 붙은 메서드 진입 전에 락을 획득하고, 종료 후 해제한다.
 *
 * 다중 인스턴스(api 스케일아웃)에서 배치/주요 트랜잭션이 동시에 중복 실행되는 것을 막는 데 쓴다.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class DistributedLock(
	/**
	 * 락 키 접두사(도메인/연산 식별자). [LockKeyConstraints]의 상수를 지정한다. (예: LockKeyConstraints.MATCH_INTEREST)
	 * SpEL이 아닌 고정 문자열이며, [keys]가 지정되면 최종 키가 "{prefix}::{key1}::{key2}..." 형태가 된다.
	 */
	val prefix: String,

	/**
	 * 락을 더 잘게 구분할 키 구성요소들의 SpEL 표현식 목록. 비워두면 [prefix]만으로 잠근다.
	 * 메서드 파라미터를 `#파라미터명`으로 참조하며, 각 표현식을 평가한 값을 [prefix] 뒤에 구분자(::)로 차례로 덧붙인다.
	 * userId뿐 아니라 matchId 등 어떤 값으로도 락을 유니크하게 잡을 수 있다.
	 *
	 * 예) ["#userId"] -> "{prefix}::{userId}" (사용자별)
	 *     ["#matchId"] -> "{prefix}::{matchId}" (매칭별)
	 *     ["#command.userId", "#type"] -> "{prefix}::{userId}::{type}" (복합 키)
	 */
	val keys: Array<String> = [],

	/** 락 획득 대기 시간. 이 시간 안에 못 얻으면 획득 실패로 본다. */
	val waitTime: Long = 5L,

	/** 락 점유(임대) 시간. 이 시간이 지나면 자동 해제되어 데드락을 방지한다. */
	val leaseTime: Long = 10L,

	/** [waitTime]·[leaseTime]의 시간 단위. */
	val timeUnit: TimeUnit = TimeUnit.SECONDS,
)
