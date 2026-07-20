package com.org.oneulsogae.core.alarm.query.dao

import java.time.LocalDateTime

/**
 * 미수신 알람 개수 조회 dao(query out-port 인터페이스). (조회 전용)
 * 실제 구현은 infra 레이어의 dao가 담당한다.
 */
interface CountUnreadAlarmDao {

	/** [since](포함) 이후 생성된 사용자의 읽지 않은(is_read=false) 알람 개수를 센다. */
	fun countUnreadByUserIdSince(userId: Long, since: LocalDateTime): Long
}
