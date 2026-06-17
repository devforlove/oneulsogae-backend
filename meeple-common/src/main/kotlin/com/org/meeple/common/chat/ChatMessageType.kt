package com.org.meeple.common.chat

/**
 * 채팅 메세지 유형.
 * [USER]는 참가자가 보낸 일반 메세지, [SYSTEM]은 시스템이 생성한 안내 메세지(예: 상대방 나감)다.
 * SYSTEM 메세지는 보낸 사람(sender)이 없다.
 */
enum class ChatMessageType {
	USER,
	SYSTEM,
}
