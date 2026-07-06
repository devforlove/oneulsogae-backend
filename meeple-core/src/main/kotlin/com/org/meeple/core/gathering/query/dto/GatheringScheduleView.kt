package com.org.meeple.core.gathering.query.dto

import com.org.meeple.common.gathering.GatheringScheduleStatus
import java.time.LocalDateTime

/**
 * 유저용 모임 상세에 포함되는 일정 한 건(read model). 한 모임의 일정 목록으로 노출된다.
 * 시간 범위는 [startAt](필수)·[endAt](선택)으로, 진행 상태는 [status]로 표현한다.
 */
data class GatheringScheduleView(
	val id: Long,
	val startAt: LocalDateTime,
	val endAt: LocalDateTime?,
	val status: GatheringScheduleStatus,
)
