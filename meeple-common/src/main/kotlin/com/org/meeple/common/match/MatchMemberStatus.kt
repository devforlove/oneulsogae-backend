package com.org.meeple.common.match

/** 매칭(소개) 참가자의 활성 상태. */
enum class MatchMemberStatus(val description: String) {

	/** 활성 상태. 매칭에 정상 참가 중인 상태. */
	ACTIVE("활성"),

	/** 비활성 상태. 채팅방 나가기 등으로 매칭 참가가 비활성화된 상태. */
	DEACTIVE("비활성"),
}
