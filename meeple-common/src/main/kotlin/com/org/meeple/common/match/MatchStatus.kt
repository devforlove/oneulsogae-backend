package com.org.meeple.common.match

/** 남녀 1:1 매칭(소개)의 진행 상태. */
enum class MatchStatus(val description: String) {

	/** 소개됨. 양쪽 응답을 기다리는 상태. */
	PROPOSED("소개됨"),

	/** 한쪽만 수락하고 상대 응답을 기다리는 상태. */
	PARTIALLY_ACCEPTED("상대 수락 대기"),

	/** 양쪽 모두 수락해 성사된 상태. */
	MATCHED("성사됨"),

	;

	/** 더 이상 응답을 받지 않는 종료 상태인지 여부. */
	fun isClosed(): Boolean = this == MATCHED
}
