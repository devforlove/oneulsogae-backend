package com.org.meeple.common.alarm

/** 알람(알림) 유형. */
enum class AlarmType(val description: String) {

	/** 상대가 나에게 관심을 보냄. */
	INTEREST_RECEIVED("관심 받음"),

	/** 매칭이 성사됨. */
	MATCHED("매칭 성사"),
}
