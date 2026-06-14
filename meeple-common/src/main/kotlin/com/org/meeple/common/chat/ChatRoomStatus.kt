package com.org.meeple.common.chat

/** 매칭 성사 후 생성되는 채팅방의 진행 상태. */
enum class ChatRoomStatus(val description: String) {

	/** 활성 상태. 두 사용자가 대화할 수 있는 상태. */
	ACTIVE("활성"),

	/** 만료 상태. 만료 시각이 지나 더 이상 대화할 수 없는 상태. */
	EXPIRED("만료"),

	/** 종료 상태. 한쪽 또는 양쪽이 채팅방을 종료한 상태. */
	CLOSED("종료"),

	;

	/** 더 이상 대화를 주고받지 않는 종료 상태인지 여부. */
	fun isClosed(): Boolean = this == EXPIRED || this == CLOSED
}
