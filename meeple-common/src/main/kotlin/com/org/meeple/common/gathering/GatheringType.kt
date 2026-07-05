package com.org.meeple.common.gathering

/** 모임([com.org.meeple.infra.gathering.command.entity.GatheringEntity])의 종류. */
enum class GatheringType(val description: String) {

	/** 1:1 로테이션. 참가자들이 1:1로 돌아가며 만나는 모임. */
	ONE_ON_ONE_ROTATION("1:1 로테이션"),

	/** 쿠킹. 함께 요리하는 모임. */
	COOKING("쿠킹"),

	/** 파티. 여럿이 어울리는 파티 모임. */
	PARTY("파티"),
}
