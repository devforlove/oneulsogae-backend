package com.org.meeple.common.gathering

/** 모임([com.org.meeple.infra.gathering.command.entity.GatheringEntity])의 진행 상태. */
enum class GatheringStatus(val description: String) {

	/** 모집중. 참가자를 모집하는 중인 상태. */
	RECRUITING("모집중"),

	/** 모집마감. 정원이 차 더 이상 참가자를 받지 않는 상태. */
	CLOSED("모집마감"),

	/** 종료. 모임이 끝난 상태. */
	FINISHED("종료"),

	/** 취소. 모임이 취소된 상태. */
	CANCELED("취소"),
}
