package com.org.meeple.api.chat

import com.org.meeple.api.chat.response.ChatRoomDetailResponse
import com.org.meeple.api.chat.response.ChatRoomResponse
import com.org.meeple.auth.AuthUser
import com.org.meeple.auth.LoginUser
import com.org.meeple.core.chat.application.port.`in`.GetChatRoomDetailUseCase
import com.org.meeple.core.chat.application.port.`in`.GetChatRoomsUseCase
import com.org.meeple.core.chat.application.port.`in`.MarkChatRoomAsReadUseCase
import com.org.meeple.core.common.response.ApiResponse
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 채팅방 엔드포인트. (모두 인증 필요)
 * - GET /rooms: 현재 로그인 사용자에게 할당된 ACTIVE 상태의 채팅방 목록을 조회한다.
 * - GET /rooms/{chatRoomId}: 특정 채팅방의 참여자 정보와 채팅 메세지를 조회한다.
 * - PUT /rooms/{chatRoomId}/read: 특정 채팅방의 안 읽은 메세지 개수를 0으로 초기화한다. (읽음 상태를 0으로 두는 멱등 연산)
 */
@RestController
@RequestMapping("/chat/v1")
class ChatController(
	private val getChatRoomsUseCase: GetChatRoomsUseCase,
	private val getChatRoomDetailUseCase: GetChatRoomDetailUseCase,
	private val markChatRoomAsReadUseCase: MarkChatRoomAsReadUseCase,
) {

	/**
	 * 현재 로그인 사용자에게 할당된 ACTIVE 상태의 채팅방 목록을 조회한다.
	 * 안 읽은 개수 등은 조회 사용자 관점으로 내려준다.
	 */
	@GetMapping("/rooms")
	fun myChatRooms(
		@LoginUser user: AuthUser,
	): ApiResponse<List<ChatRoomResponse>> =
		ApiResponse.success(ChatRoomResponse.listOf(getChatRoomsUseCase.getActiveChatRooms(user.id)))

	/**
	 * 특정 채팅방의 참여자 정보와 채팅 메세지를 조회한다. (조회자는 그 채팅방의 참가자여야 한다)
	 * 메세지는 최근부터 [size]건을 내려주며, [cursor](가장 오래된 메세지 id)를 넘기면 그보다 과거 구간을 잇는다.
	 */
	@GetMapping("/rooms/{chatRoomId}")
	fun chatRoomDetail(
		@LoginUser user: AuthUser,
		@PathVariable chatRoomId: Long,
		@RequestParam(required = false) cursor: Long?,
		@RequestParam(defaultValue = "100") size: Int,
	): ApiResponse<ChatRoomDetailResponse> =
		ApiResponse.success(
			ChatRoomDetailResponse.of(
				getChatRoomDetailUseCase.getChatRoomDetail(
					userId = user.id,
					chatRoomId = chatRoomId,
					beforeMessageId = cursor,
					size = size,
				),
			),
		)

	/**
	 * 특정 채팅방의 안 읽은 메세지 개수를 0으로 초기화한다. (요청 사용자는 그 방의 참가자여야 한다)
	 * 사용자가 채팅방에 들어와 메세지를 모두 확인했을 때 호출한다. 읽음 상태는 참가자 본인 것만 갱신된다.
	 * 읽음 상태를 0으로 두는 멱등 연산이라 PUT으로 둔다. (여러 번 호출해도 결과가 같다)
	 */
	@PutMapping("/rooms/{chatRoomId}/read")
	fun markChatRoomAsRead(
		@LoginUser user: AuthUser,
		@PathVariable chatRoomId: Long,
	): ApiResponse<Unit> {
		markChatRoomAsReadUseCase.markAsRead(userId = user.id, chatRoomId = chatRoomId)
		return ApiResponse.success()
	}
}
