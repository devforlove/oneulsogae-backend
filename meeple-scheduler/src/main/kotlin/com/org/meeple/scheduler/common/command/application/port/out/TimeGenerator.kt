package com.org.meeple.scheduler.common.command.application.port.out

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 현재 시각 제공 아웃포트.
 * 배치 로직이 [java.time.LocalDateTime.now]를 직접 호출하지 않고 이 인터페이스에 의존하게 해, 테스트에서 시각을 고정할 수 있게 한다.
 * (core의 동일 추상화에 의존하지 않도록 scheduler가 자체 포트로 둔다. 구현은 infra 어댑터가 담당한다)
 */
interface TimeGenerator {

	fun now(): LocalDateTime

	/** 오늘 날짜. "하루에 한 번" 같은 일자 기준 판단에 사용한다. */
	fun today(): LocalDate = now().toLocalDate()
}
