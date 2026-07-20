package com.org.oneulsogae.api.offline.response

import com.org.oneulsogae.common.gathering.GatheringScheduleStatus

/**
 * 유저용 일정 아이템(성별 셀렉트박스)의 표시 상태. 일정 자체 상태([GatheringScheduleStatus])에
 * 성별 정원 소진([SOLD_OUT])을 더한 값이다.
 */
enum class GatheringScheduleItemStatus(val description: String) {
	SCHEDULED("예정"),
	COMPLETED("종료"),
	CANCELED("취소"),
	SOLD_OUT("소진됨"),

	;

	companion object {
		/** 해당 성별 정원이 소진([soldOut])되면 SOLD_OUT, 아니면 일정 상태를 그대로 매핑한다. */
		fun of(scheduleStatus: GatheringScheduleStatus, soldOut: Boolean): GatheringScheduleItemStatus =
			if (soldOut) {
				SOLD_OUT
			} else {
				when (scheduleStatus) {
					GatheringScheduleStatus.SCHEDULED -> SCHEDULED
					GatheringScheduleStatus.COMPLETED -> COMPLETED
					GatheringScheduleStatus.CANCELED -> CANCELED
				}
			}
	}
}
