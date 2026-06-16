package com.org.meeple.chatting.chat.adapter.web

import com.org.meeple.auth.userIdOrNull
import com.org.meeple.chatting.chat.adapter.web.request.ChatMessageSendRequest
import com.org.meeple.chatting.chat.adapter.web.response.ChatErrorResponse
import com.org.meeple.chatting.chat.adapter.web.response.ChatMessageDto
import com.org.meeple.chatting.chat.application.ChatErrorCode
import com.org.meeple.chatting.chat.application.port.`in`.SendChatMessageUseCase
import com.org.meeple.chatting.chat.application.port.`in`.command.SendChatMessageCommand
import com.org.meeple.chatting.chat.application.port.`in`.result.SentChatMessageResult
import com.org.meeple.chatting.common.error.ChatException
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.Header
import org.springframework.messaging.handler.annotation.MessageExceptionHandler
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.messaging.simp.annotation.SendToUser
import org.springframework.stereotype.Controller
import java.security.Principal

/**
 * 채팅 STOMP 메시지 컨트롤러.
 *
 * 클라이언트는 `/app/{roomId}`로 발행(SEND)하고, 서버는 발신자 검증·저장·방 갱신을 거친 뒤
 * 그 방을 구독(`/topic/{roomId}`)한 참가자에게 저장된 메세지를 브로드캐스트한다.
 * 발신자는 CONNECT 시 인증돼 세션에 주입된 Principal(userId)이며, 본문만 클라이언트가 보낸다.
 */
@Controller
class ChatMessageController(
	private val messageTemplate: SimpMessagingTemplate,
	private val sendChatMessageUseCase: SendChatMessageUseCase,
) {

	/** `/app/{roomId}`로 발행된 메세지를 처리한다. */
	@MessageMapping("/{roomId}")
	fun sendToRoom(
		@DestinationVariable roomId: Long,
		request: ChatMessageSendRequest,
		principal: Principal,
	) {
		// CONNECT에서 accessor.user로 주입한 인증 주체에서 userId를 꺼낸다. (인터셉터와 동일한 추출 경로)
		val senderId: Long = principal.userIdOrNull() ?: throw ChatException(ChatErrorCode.AUTHENTICATION_REQUIRED)

		val sentMessage: SentChatMessageResult = sendChatMessageUseCase.send(
			SendChatMessageCommand(chatRoomId = roomId, senderId = senderId, content = request.message),
		)

		messageTemplate.convertAndSend("/topic/$roomId", ChatMessageDto.from(sentMessage))
	}

	/**
	 * 발행(SEND) 처리 중 발생한 [ChatException]을 발신자 개인 큐(`/user/queue/errors`)로 통지한다.
	 * SUBSCRIBE 거부와 동일하게, 연결을 끊지 않고 사유만 본인에게 전달한다. (broadcast=false: 메세지를 보낸 그 세션에만 전달)
	 * [destination]은 거부된 발행 목적지(예: `/app/42`)다.
	 */
	@MessageExceptionHandler(ChatException::class)
	@SendToUser(destinations = ["/queue/errors"], broadcast = false)
	fun handleChatException(
		exception: ChatException,
		@Header(SimpMessageHeaderAccessor.DESTINATION_HEADER, required = false) destination: String?,
	): ChatErrorResponse =
		ChatErrorResponse(
			code = exception.errorCode.code,
			message = exception.errorCode.message,
			destination = destination,
		)
}
