package com.org.meeple.common.alarm

/** 알람(알림) 유형. */
enum class AlarmType(val description: String) {

	/** [1:1 매칭] 상대가 나에게 관심을 보냄. */
	ONE_TO_ONE_INTEREST_RECEIVED("관심 받음"),

	/** [1:1 매칭] 매칭이 성사됨. */
	ONE_TO_ONE_MATCHED("매칭 성사"),

	/** [다대다 매칭] 상대가 나에게 관심을 보냄. */
	MANY_TO_MANY_INTEREST_RECEIVED("관심 받음"),

	/** [다대다 매칭] 매칭이 성사됨. */
	MANY_TO_MANY_MATCHED("매칭 성사"),

	/** [팀 매칭] 팀에 초대받음. */
	TEAM_INVITATION_RECEIVED("팀 초대 받음"),
}
