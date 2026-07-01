package com.org.meeple.common.match

/**
 * 남녀 1:1 매칭(소개)의 진행 상태.
 *
 * @property listPriority 매칭 목록 화면 정렬 우선순위. 낮을수록 먼저 노출한다. (성사 > 상대 수락 대기 > 소개됨 > 종료)
 */
enum class MatchStatus(val description: String, val listPriority: Int) {

	/** 소개됨. 양쪽 응답을 기다리는 상태. */
	PROPOSED("소개됨", 2),

	/** 한쪽만 수락하고 상대 응답을 기다리는 상태. */
	PARTIALLY_ACCEPTED("상대 수락 대기", 1),

	/** 양쪽 모두 수락해 성사된 상태. */
	MATCHED("성사됨", 0),

	/** 종료된 상태. (채팅방 나가기 등으로 관계가 끝나 제거된 매칭) */
	CLOSED("종료됨", 3),

	;

	/** 더 이상 응답을 받지 않는 종료 상태인지 여부. */
	fun isClosed(): Boolean = this == MATCHED || this == CLOSED
}
