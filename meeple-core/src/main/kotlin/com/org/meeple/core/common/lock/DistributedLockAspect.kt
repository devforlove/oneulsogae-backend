package com.org.meeple.core.common.lock

import com.org.meeple.core.common.error.BusinessException
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.core.DefaultParameterNameDiscoverer
import org.springframework.core.Ordered
import org.springframework.core.ParameterNameDiscoverer
import org.springframework.core.annotation.Order
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.stereotype.Component

/**
 * [DistributedLock]이 붙은 메서드를 분산 락으로 감싼다.
 *
 * 진입 시 [DistributedLockPort.tryLock]으로 락을 얻고, 못 얻으면 [LockErrorCode.LOCK_ACQUISITION_FAILED]를 던진다.
 * 메서드 종료 후 [DistributedLockPort.unlock]으로 해제한다. (실제 락 메커니즘은 infra 어댑터가 구현)
 *
 * 어드바이스 우선순위를 거의 가장 높게([Ordered.HIGHEST_PRECEDENCE] + 1) 둬, 트랜잭션 어드바이스(@Transactional, 기본 [Ordered.LOWEST_PRECEDENCE])보다
 * 바깥에서 동작한다. 따라서 같은 메서드에 [DistributedLock]과 @Transactional을 함께 붙여도
 * "락 획득 → 트랜잭션 시작 → 커밋 → 락 해제" 순서가 보장되어, 커밋 이전에 락이 풀리는 일이 없다.
 *
 * 정확히 [Ordered.HIGHEST_PRECEDENCE]가 아니라 +1을 쓰는 이유: 스프링이 포인트컷 인자 바인딩(@annotation(distributedLock))을
 * 위해 현재 호출 정보를 노출하는 내부 인터셉터(ExposeInvocationInterceptor)도 [Ordered.HIGHEST_PRECEDENCE]에 등록된다.
 * 이 어스펙트가 같은 우선순위를 가지면 체인에서 ExposeInvocationInterceptor보다 앞설 수 있어
 * JoinPointMatch 바인딩에 실패한다("Required to bind 2 arguments, but only bound 1"). +1로 한 칸 양보해 충돌을 피한다.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class DistributedLockAspect(
	private val distributedLockPort: DistributedLockPort,
) {

	private val parser: SpelExpressionParser = SpelExpressionParser()
	private val parameterNameDiscoverer: ParameterNameDiscoverer = DefaultParameterNameDiscoverer()

	@Around("@annotation(distributedLock)")
	fun lock(joinPoint: ProceedingJoinPoint, distributedLock: DistributedLock): Any? {
		val signature: MethodSignature = joinPoint.signature as MethodSignature
		val key: String = buildKey(signature, joinPoint.args, distributedLock)

		val acquired: Boolean = try {
			distributedLockPort.tryLock(key, distributedLock.waitTime, distributedLock.leaseTime, distributedLock.timeUnit)
		} catch (e: InterruptedException) {
			Thread.currentThread().interrupt()
			throw BusinessException(LockErrorCode.LOCK_INTERRUPTED)
		}

		if (!acquired) {
			throw BusinessException(LockErrorCode.LOCK_ACQUISITION_FAILED)
		}

		try {
			return joinPoint.proceed()
		} finally {
			distributedLockPort.unlock(key)
		}
	}

	/**
	 * 최종 락 키를 만든다. [DistributedLock.prefix]는 고정 문자열 그대로 쓰고,
	 * [DistributedLock.keys]가 지정되면 각 SpEL 표현식을 평가한 값을 "{delimiter}{value}"로 차례로 덧붙인다.
	 * 키 구성요소는 userId·matchId 등 무엇이든 될 수 있다. (예: MATCH_INTEREST::42, MATCH_RESPONSE::1001)
	 */
	private fun buildKey(signature: MethodSignature, args: Array<Any?>, distributedLock: DistributedLock): String {
		if (distributedLock.keys.isEmpty()) {
			return distributedLock.prefix
		}
		val context: StandardEvaluationContext = bindParameters(signature, args)
		return buildString {
			append(distributedLock.prefix)
			for (keyExpression: String in distributedLock.keys) {
				append(LockKeyConstraints.DELIMITER)
				append(evaluate(keyExpression, context))
			}
		}
	}

	/** 메서드 파라미터를 SpEL 변수(#param)로 바인딩한 평가 컨텍스트를 만든다. */
	private fun bindParameters(signature: MethodSignature, args: Array<Any?>): StandardEvaluationContext {
		val context: StandardEvaluationContext = StandardEvaluationContext()
		val parameterNames: Array<out String?>? = parameterNameDiscoverer.getParameterNames(signature.method)
		if (parameterNames != null) {
			for (i in parameterNames.indices) {
				context.setVariable(parameterNames[i], args[i])
			}
		}
		return context
	}

	/** SpEL 표현식을 문자열로 평가한다. 평가 결과가 null이면 표현식 원문을 그대로 쓴다. */
	private fun evaluate(expression: String, context: StandardEvaluationContext): String =
		parser.parseExpression(expression).getValue(context, String::class.java) ?: expression
}
