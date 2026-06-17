package com.org.meeple.core.alarm.query.dto

/**
 * 알람 발신 유저 프로필([AlarmFrom]) 목록의 일급 컬렉션(first-class collection).
 * 원시 List를 그대로 노출하지 않고 감싸, 컬렉션에 대한 동작을 한곳에 응집시킨다.
 */
data class AlarmFroms(
	val values: List<AlarmFrom>,
) {

	/** 발신 유저 수. */
	val size: Int
		get() = values.size

	companion object {

		/** 빈 발신 유저 목록. */
		fun empty(): AlarmFroms = AlarmFroms(emptyList())
	}
}
