package com.org.oneulsogae.core.alarm.command.application.port.`in`

/** 내 알람 일괄 읽음 처리 인포트(유스케이스). */
interface MarkAlarmsReadUseCase {

	/** 사용자의 최근 보관 기간 이내 읽지 않은 알람을 모두 읽음 처리한다. (여러 번 호출해도 결과가 같은 멱등 연산) */
	fun markAllRead(userId: Long)
}
