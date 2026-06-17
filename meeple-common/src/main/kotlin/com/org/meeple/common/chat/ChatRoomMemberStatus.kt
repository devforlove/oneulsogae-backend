package com.org.meeple.common.chat

/** 채팅방 참가자의 활성 상태. */
enum class ChatRoomMemberStatus(val description: String) {

	/** 활성 상태. 채팅방에 정상 참가 중인 상태. */
	ACTIVE("활성"),

	/** 비활성 상태. 채팅방 참가가 비활성화된 상태. */
	DEACTIVE("비활성"),
}
