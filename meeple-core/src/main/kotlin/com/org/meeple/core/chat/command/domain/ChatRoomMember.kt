package com.org.meeple.core.chat.command.domain

import com.org.meeple.common.chat.ChatRoomMemberStatus
import java.time.LocalDateTime

/**
 * 채팅방에 참가한 사용자 한 명의 참가 상태 도메인 모델.
 * 기존 [ChatRoom]은 안 읽은 개수를 성별(male/female)로 비정규화해 한 행에 들고 있으나,
 * 이 모델은 (chatRoomId, userId) 한 쌍을 한 행으로 두어 참가자별 읽음 상태를 정규화한다.
 * [unreadCount]/[lastReadAt]은 해당 참가자 본인의 읽음 상태이며, 상대가 보낸 메세지가 쌓이면 증가하고 본인이 읽으면 0으로 되돌린다.
 * [exitedAt]이 채워지면 채팅방을 나간 것으로 본다.
 * [deletedAt]이 채워지면 소프트 삭제된(나가기로 목록·조회에서 제외된) 참가자다.
 * 영속성은 [com.org.meeple.infra.chat.command.entity.ChatRoomMemberEntity]가 담당한다.
 */
data class ChatRoomMember(
	val id: Long = 0,
	val chatRoomId: Long,
	val userId: Long,
	/** TEAM 매칭 방에서 이 참가자가 속한 팀 id. SOLO 방은 null. 목록 조회에서 내 팀/상대 팀 구분에 쓴다. */
	val teamId: Long? = null,
	val status: ChatRoomMemberStatus = ChatRoomMemberStatus.ACTIVE,
	val unreadCount: Int = 0,
	val lastReadAt: LocalDateTime? = null,
	val lastReadMessageId: Long? = null,
	val joinedAt: LocalDateTime,
	val exitedAt: LocalDateTime? = null,
	val deletedAt: LocalDateTime? = null,
) {

	/** 활성(참가 중) 상태인지 여부. (나간(DEACTIVE) 참가자는 false) */
	val isActive: Boolean
		get() = status == ChatRoomMemberStatus.ACTIVE

	/** 채팅방을 나갔는지 여부. */
	val isExited: Boolean
		get() = exitedAt != null

	/** 소프트 삭제(나가기) 되었는지 여부. */
	val isDeleted: Boolean
		get() = deletedAt != null

	/**
	 * 이 참가자가 메세지 한 건을 새로 받은 것으로 보고, 안 읽은 개수만 1 증가시킨 새 모델을 반환한다.
	 * (상대가 보낸 메세지의 수신자 쪽에서 호출한다. 참가/미종료 검증은 호출 측 책임)
	 */
	fun receiveMessage(): ChatRoomMember =
		copy(unreadCount = unreadCount + 1)

	/**
	 * 이 참가자가 메세지를 모두 확인한 것으로 보고, 안 읽은 개수를 0으로 되돌리고 마지막 읽은 시각을 [now]로 갱신한 새 모델을 반환한다.
	 * (참가자 검증은 호출 측 책임)
	 */
	fun markAsRead(now: LocalDateTime): ChatRoomMember =
		copy(unreadCount = 0, lastReadAt = now)

	/** 이 참가자가 [now]에 채팅방을 나간 것으로 전이한 새 모델을 반환한다. */
	fun exit(now: LocalDateTime): ChatRoomMember =
		copy(exitedAt = now)

	/**
	 * 이 참가자를 비활성([ChatRoomMemberStatus.DEACTIVE]) 상태로 전이한 새 모델을 반환한다. (나가기)
	 * 행을 소프트 삭제하지 않고 상태만 바꾸므로, 프로필 노출 등은 유지하되 활성 참가자 판정(접근·종료·목록)에서는 제외된다.
	 */
	fun deactivate(): ChatRoomMember =
		copy(status = ChatRoomMemberStatus.DEACTIVE)

	/**
	 * 이 참가자를 [now]에 비활성([ChatRoomMemberStatus.DEACTIVE]) 전이 + 소프트 삭제(나가기)한 새 모델을 반환한다.
	 * 저장하면 status가 DEACTIVE가 되고 [deletedAt]이 채워져 조회·접근에서 제외된다. (참가자 검증은 호출 측 책임)
	 */
	fun delete(now: LocalDateTime): ChatRoomMember =
		copy(status = ChatRoomMemberStatus.DEACTIVE, deletedAt = now)

	companion object {

		/** [userId] 사용자를 [chatRoomId] 채팅방에 [now]에 참가시킨 신규 참가자를 생성한다. ([teamId]는 TEAM 방의 소속 팀, SOLO는 null. 안 읽은 개수 0, 미확인 상태) */
		fun join(chatRoomId: Long, userId: Long, teamId: Long?, now: LocalDateTime): ChatRoomMember =
			ChatRoomMember(
				chatRoomId = chatRoomId,
				userId = userId,
				teamId = teamId,
				joinedAt = now,
			)
	}
}
