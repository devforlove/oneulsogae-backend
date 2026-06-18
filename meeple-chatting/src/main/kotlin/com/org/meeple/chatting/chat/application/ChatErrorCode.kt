package com.org.meeple.chatting.chat.application

/**
 * 채팅(WebSocket/STOMP) 도메인 에러 코드.
 * chatting 모듈은 core에 의존하지 않으므로 core의 ErrorCode/BusinessException 대신 자체 에러 모델을 둔다.
 * [ChatException]에 넘겨 사용하며, 위반 시 STOMP ERROR 프레임으로 클라이언트에 전달된다. (HTTP status는 WS 경계라 두지 않는다)
 */
enum class ChatErrorCode(
	val code: String,
	val message: String,
) {

	/** CONNECT 시점에 토큰이 없거나 만료·위조되어 유효하지 않은 경우. */
	AUTHENTICATION_REQUIRED("CHAT-WS-001", "인증이 필요합니다. 다시 로그인해 주세요."),

	/** 토큰은 유효하나 다른 기기/브라우저의 새 로그인에 밀려난(단일 활성 세션에서 탈락한) 세션인 경우. */
	SESSION_TAKEN_OVER("CHAT-WS-007", "다른 기기/브라우저의 로그인으로 종료된 세션입니다. 다시 로그인해 주세요."),

	/** 참가하지 않은 채팅방을 구독·발송하려는 경우. */
	NOT_CHAT_ROOM_PARTICIPANT("CHAT-WS-002", "해당 채팅방의 참가자가 아닙니다."),

	/** 대상 채팅방이 존재하지 않는 경우. */
	CHAT_ROOM_NOT_FOUND("CHAT-WS-003", "채팅방을 찾을 수 없습니다."),

	/** 이미 만료·종료된 채팅방에 발송하려는 경우. */
	CHAT_ROOM_CLOSED("CHAT-WS-004", "이미 종료된 채팅방입니다."),

	/** 메세지 본문이 비어 있는 경우. */
	EMPTY_MESSAGE("CHAT-WS-005", "메세지 내용이 비어 있습니다."),

	/** 메세지 본문이 최대 길이를 초과한 경우. */
	MESSAGE_TOO_LONG("CHAT-WS-006", "메세지 내용이 너무 깁니다."),
}
