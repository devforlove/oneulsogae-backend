package com.org.meeple.common.gathering

/** 모임 일정([com.org.meeple.infra.gathering.command.entity.GatheringScheduleEntity])의 진행 상태. */
enum class GatheringScheduleStatus(val description: String) {

	/** 예정. 일정 생성 직후의 초기 상태. (아직 시작 전) */
	SCHEDULED("예정"),

	/** 종료. 일정이 정상적으로 끝난 상태. */
	COMPLETED("종료"),

	/** 취소. 일정이 취소된 상태. */
	CANCELED("취소"),
}
