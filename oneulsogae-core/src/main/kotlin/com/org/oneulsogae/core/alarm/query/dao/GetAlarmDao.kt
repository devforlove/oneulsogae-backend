package com.org.oneulsogae.core.alarm.query.dao

import com.org.oneulsogae.core.alarm.query.dto.AlarmViews
import java.time.LocalDateTime

/**
 * 알람 조회 dao(query out-port 인터페이스). (조회 전용 read model 반환)
 * 일급 컬렉션([AlarmViews])으로 반환하며, 실제 구현은 infra 레이어의 dao가 담당한다.
 */
interface GetAlarmDao {

	/** [since](포함) 이후 생성된 사용자의 알람을 생성 시각 최신순으로 조회한다. (없으면 빈 [AlarmViews]) */
	fun findByUserIdSince(userId: Long, since: LocalDateTime): AlarmViews
}
