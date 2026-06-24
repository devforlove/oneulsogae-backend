package com.org.meeple.core.chat.command.domain

import com.org.meeple.common.chat.ChatRoomMatchType
import com.org.meeple.common.chat.ChatRoomStatus
import com.org.meeple.core.chat.ChatErrorCode
import com.org.meeple.core.common.error.BusinessException
import java.time.Duration
import java.time.LocalDateTime

/**
 * 채팅방의 방 공통 상태 도메인 모델.
 * 누가 참가했는지(참가자)는 방이 아니라 [ChatRoomMember]가 단일 진실원천으로 보관하므로, 방은 참가자 식별 컬럼을 들지 않는다.
 * (1:1·그룹챗 모두 같은 모델을 쓰며, 참가/상대 식별은 [ChatRoomMembers]가 담당한다)
 * [expiredAt]까지만 대화할 수 있고, 그 시각이 지나면 만료된 채팅방([ChatRoomStatus.EXPIRED])으로 본다.
 * 한쪽 또는 양쪽이 종료하면 [ChatRoomStatus.CLOSED] 상태가 된다.
 * 참가자별 안 읽은 메세지 개수·읽음 시각도 방이 아니라 참가자 단위로 [ChatRoomMember]가 보관한다.
 * [lastMessage]/[lastMessageAt]은 방 공통으로, 마지막으로 주고받은 메세지와 그 수신 시각이다.
 * 영속성은 [com.org.meeple.infra.chat.command.entity.ChatRoomEntity]가 담당한다.
 */
data class ChatRoom(
	val id: Long = 0,
	val matchType: ChatRoomMatchType,
	val matchId: Long,
	val status: ChatRoomStatus = ChatRoomStatus.ACTIVE,
	val expiredAt: LocalDateTime,
	val lastMessage: String? = null,
	val lastMessageAt: LocalDateTime? = null,
	val deletedAt: LocalDateTime? = null,
) {

	/** 더 이상 대화를 주고받지 않는 종료 상태인지 여부. */
	val isClosed: Boolean
		get() = status.isClosed()

	/**
	 * 이 채팅방이 이미 종료된 상태인지 검증한다.
	 * 이미 종료된 채팅방이면 [ChatErrorCode.CHAT_ROOM_ALREADY_CLOSED]를 던진다.
	 * (참가 여부는 방이 아니라 [com.org.meeple.core.chat.query.dto.ChatParticipants.validateParticipant]가 검증한다)
	 */
	fun validateNotClosed() {
		if (isClosed) {
			throw BusinessException(ChatErrorCode.CHAT_ROOM_ALREADY_CLOSED)
		}
	}

	/** [now] 기준으로 만료 시각이 지났는지 여부. */
	fun isExpired(now: LocalDateTime): Boolean =
		!now.isBefore(expiredAt)

	/** 채팅방을 만료 상태로 전이한 새 모델을 반환한다. */
	fun expire(): ChatRoom =
		copy(status = ChatRoomStatus.EXPIRED)

	/** 채팅방을 종료 상태로 전이한 새 모델을 반환한다. */
	fun close(): ChatRoom =
		copy(status = ChatRoomStatus.CLOSED)

	/**
	 * 이 채팅방을 [now]에 종료([ChatRoomStatus.CLOSED])하고 소프트 삭제(제거)한 새 모델을 반환한다.
	 * 마지막 참가자가 나가 방이 닫힐 때 호출한다. 저장하면 상태가 CLOSED가 되고 deletedAt이 채워져 이후 조회에서 제외된다.
	 * 참가자(ChatRoomMember) 소프트 삭제는 별도 행이라 호출 측이 함께 처리한다.
	 */
	fun delete(now: LocalDateTime): ChatRoom =
		copy(status = ChatRoomStatus.CLOSED, deletedAt = now)

	/**
	 * 새 메세지([content])를 방의 마지막 메세지/수신 시각([now])으로 반영한 새 모델을 반환한다.
	 * 참가자별 안 읽은 개수 증가는 수신자 쪽 [ChatRoomMember.receiveMessage]가 담당하므로, 여기서는 방 공통 상태만 갱신한다.
	 * (참가자/미종료 검증은 호출 측 책임)
	 */
	fun receiveMessage(content: String, now: LocalDateTime): ChatRoom =
		copy(lastMessage = content, lastMessageAt = now)

	companion object {

		/** 채팅방의 유효 기간. 생성 시각으로부터 이 기간이 지나면 만료된 것으로 본다. */
		val EXPIRATION: Duration = Duration.ofDays(3)

		/**
		 * 신규 채팅방을 생성한다. (status ACTIVE)
		 * 어느 매칭에서 생성됐는지 [matchType](solo/team)+[matchId]로 보관하고, 만료 시각(expiredAt)은 [now] + [EXPIRATION]으로 설정한다.
		 * 참가자는 방이 아니라 [ChatRoomMember]로 따로 생성한다. (방은 참가자 식별 컬럼을 들지 않는다)
		 */
		fun open(matchType: ChatRoomMatchType, matchId: Long, now: LocalDateTime): ChatRoom =
			ChatRoom(
				matchType = matchType,
				matchId = matchId,
				status = ChatRoomStatus.ACTIVE,
				expiredAt = now.plus(EXPIRATION),
			)
	}
}
