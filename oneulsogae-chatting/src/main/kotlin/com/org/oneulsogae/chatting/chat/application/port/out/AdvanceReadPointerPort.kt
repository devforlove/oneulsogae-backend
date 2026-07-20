package com.org.oneulsogae.chatting.chat.application.port.out

import java.time.LocalDateTime

/**
 * 읽음 포인터 전진 아웃포트. (chatting 소유, infra 어댑터가 구현)
 * 참가자를 도메인으로 로드하지 않고, 한 참가자의 last_read_message_id를 **forward-only 조건부 UPDATE**로 전진시키며 뱃지(unread_count)를 0으로 되돌린다.
 * 이미 같거나 더 앞선 포인터면 갱신하지 않는다(역행 방지). 갱신된 행 수를 반환한다(0이면 변화 없음 → 브로드캐스트 생략).
 */
interface AdvanceReadPointerPort {

	/** [chatRoomId] 방의 활성 참가자 [userId]의 읽음 포인터를 [lastReadMessageId]로 전진시키고 뱃지를 0으로 리셋한다. (forward-only) 갱신 행 수 반환. */
	fun advance(chatRoomId: Long, userId: Long, lastReadMessageId: Long, now: LocalDateTime): Int
}
