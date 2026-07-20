package com.org.oneulsogae.core.chat

import com.org.oneulsogae.core.common.error.ErrorCode
import org.springframework.http.HttpStatus

/**
 * 채팅 도메인 에러 코드. (command·query 양쪽이 공유하므로 chat 루트에 둔다)
 * [com.org.oneulsogae.core.common.error.BusinessException]에 넘겨 사용한다.
 */
enum class ChatErrorCode(
	override val code: String,
	override val message: String,
	override val status: HttpStatus,
) : ErrorCode {

	CHAT_ROOM_NOT_FOUND("CHAT-001", "채팅방을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
	NOT_CHAT_ROOM_PARTICIPANT("CHAT-002", "해당 채팅방의 참가자가 아닙니다.", HttpStatus.FORBIDDEN),
	CHAT_ROOM_ALREADY_CLOSED("CHAT-003", "이미 종료된 채팅방입니다.", HttpStatus.CONFLICT),
	// CHAT-004(USER_GENDER_NOT_FOUND)는 채팅방 목록을 참가자(ChatRoomMember) 기반으로 조회하도록 바뀌면서 제거됐다. (성별 불필요)
	EMPTY_MESSAGE("CHAT-005", "메세지 내용이 비어 있습니다.", HttpStatus.BAD_REQUEST),
	MESSAGE_TOO_LONG("CHAT-006", "메세지 내용이 너무 깁니다.", HttpStatus.BAD_REQUEST),
}
