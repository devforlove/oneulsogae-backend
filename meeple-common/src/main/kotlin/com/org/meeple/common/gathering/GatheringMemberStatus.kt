package com.org.meeple.common.gathering

/** 모임 참가자([com.org.meeple.infra.gathering.command.entity.GatheringMemberEntity])의 상태. */
enum class GatheringMemberStatus(val description: String) {

	/** 참가. 모임에 정상 참가 중인 상태. */
	JOINED("참가"),

	/** 참가취소. 모임 참가를 취소한 상태. */
	CANCELED("참가취소"),
}
