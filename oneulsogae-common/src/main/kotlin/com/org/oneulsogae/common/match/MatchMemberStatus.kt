package com.org.oneulsogae.common.match

/** 매칭(소개) 참가자의 상태. WAITING(대기) → APPLY(신청) → ACTIVE(성사·활성) / DEACTIVE(채팅 나감). */
enum class MatchMemberStatus(val description: String) {

	/** 대기. 소개 직후, 관심 미신청 상태. */
	WAITING("대기"),

	/** 신청. 관심을 신청했으나 매치가 아직 성사되지 않은 상태. */
	APPLY("신청"),

	/** 활성. 전원 신청해 매치가 성사된 활성 상태. */
	ACTIVE("활성"),

	/** 비활성. 채팅방 나가기 등으로 매칭 참가가 비활성화된 상태. */
	DEACTIVE("비활성"),
}
