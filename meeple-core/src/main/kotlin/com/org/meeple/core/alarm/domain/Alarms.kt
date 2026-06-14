package com.org.meeple.core.alarm.domain

/**
 * 사용자 알람([Alarm]) 목록의 일급 컬렉션(first-class collection).
 * 원시 List를 그대로 노출하지 않고 감싸, 컬렉션에 대한 동작을 한곳에 응집시킨다.
 */
data class Alarms(
	val values: List<Alarm>,
) {

	/** 알람 개수. */
	val size: Int
		get() = values.size

	/** 읽지 않은 알람 개수. */
	val unreadCount: Int
		get() = values.count { !it.isRead }

	/** 비어 있는지 여부. */
	fun isEmpty(): Boolean = values.isEmpty()

	companion object {

		/** 빈 알람 목록. */
		fun empty(): Alarms = Alarms(emptyList())
	}
}
