package com.org.meeple.core.alarm.query.dto

/**
 * 사용자 알람([AlarmView]) 목록의 일급 컬렉션(first-class collection).
 * 원시 List를 그대로 노출하지 않고 감싸, 컬렉션에 대한 동작을 한곳에 응집시킨다.
 */
data class AlarmViews(
	val values: List<AlarmView>,
) {

	/** 알람 개수. */
	val size: Int
		get() = values.size

	/** 읽지 않은 알람 개수. */
	val unreadCount: Int
		get() = values.count { !it.isRead }

	/** 비어 있는지 여부. */
	fun isEmpty(): Boolean = values.isEmpty()

	/** 알람을 유발한 발신 유저 id들을 중복 없이 모은다. (없는 알람은 제외) 발신 유저 프로필 일괄 조회에 쓴다. */
	fun fromUserIds(): Set<Long> = values.mapNotNull { it.fromUserId }.toSet()

	companion object {

		/** 빈 알람 목록. */
		fun empty(): AlarmViews = AlarmViews(emptyList())
	}
}
