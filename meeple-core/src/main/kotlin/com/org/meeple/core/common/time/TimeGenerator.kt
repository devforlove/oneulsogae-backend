package com.org.meeple.core.common.time

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 현재 시각 제공 추상화.
 * 도메인/애플리케이션이 [java.time.LocalDateTime.now]를 직접 호출하지 않고 이 인터페이스에 의존하게 하여,
 * 테스트에서 시각을 고정하거나 대체할 수 있도록 한다.
 */
interface TimeGenerator {

	fun now(): LocalDateTime

	/** 오늘 날짜. "하루에 한 번" 같은 일자 기준 판단에 사용한다. */
	fun today(): LocalDate = now().toLocalDate()
}
