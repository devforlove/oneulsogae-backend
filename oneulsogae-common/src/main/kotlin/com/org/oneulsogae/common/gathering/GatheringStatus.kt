package com.org.oneulsogae.common.gathering

/** 모임([com.org.oneulsogae.infra.gathering.command.entity.GatheringEntity])의 진행 상태. */
enum class GatheringStatus(val description: String) {

	/** 활성화. 등록 직후의 초기 상태이자 참가자를 받는 상태. */
	RECRUITING("활성화"),

	/** 취소. 모임이 취소된 상태. */
	CANCELED("취소"),
}
