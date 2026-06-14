package com.org.meeple.chatting.controller

import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Controller
import java.security.Principal

/**
 * 채팅 STOMP 메시지 컨트롤러. (raw `TextWebSocketHandler`를 대체)
 *
 * 클라이언트는 `/app/chat.send`로 발행(SEND)하고, 서버는 해당 방을 구독(`/topic/room.{roomId}`)한 참가자에게 전달한다.
 * 현재는 골격만 제공하며, 메시지 파싱·검증·저장과 실제 전달 로직은 추후 채워 넣는다.
 */
@Controller
class ChatMessageController(
	private val messagingTemplate: SimpMessagingTemplate,
) {

	/** 클라이언트가 `/app/chat.send`로 보낸 메시지를 처리한다. */
	@MessageMapping("/chat.send")
	fun send(@Payload payload: String, principal: Principal?) {
		// TODO: payload(예: roomId/내용)를 파싱·검증·저장한 뒤, 방 구독자에게 전달한다.
		//       예) messagingTemplate.convertAndSend("/topic/room.$roomId", outgoing)
	}
}
