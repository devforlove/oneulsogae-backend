package com.org.meeple.common.gathering

/** 모임 참가자([com.org.meeple.infra.gathering.command.entity.GatheringMemberEntity])의 상태. */
enum class GatheringMemberStatus(val description: String) {

	/** 승인대기. 결제완료 접수 직후 운영자 승인을 기다리는 상태. 정원에 포함된다. */
	PENDING("승인대기"),

	/** 참가. 모임에 정상 참가 중인 상태. */
	JOINED("참가"),

	/** 거절. 운영자가 접수를 거절한 상태(입금 미확인 등). 정원에서 제외된다. */
	REJECTED("거절"),

	/** 참가취소. 모임 참가를 취소한 상태. */
	CANCELED("참가취소"),
}
