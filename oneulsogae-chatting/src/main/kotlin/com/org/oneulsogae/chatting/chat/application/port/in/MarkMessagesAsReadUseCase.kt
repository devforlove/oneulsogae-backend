package com.org.oneulsogae.chatting.chat.application.port.`in`

import com.org.oneulsogae.chatting.chat.application.port.`in`.command.MarkMessagesAsReadCommand
import com.org.oneulsogae.chatting.chat.application.port.`in`.result.MarkMessagesAsReadResult

/**
 * 읽음 보고 인포트(유스케이스).
 * 보고자가 그 방의 활성 참가자인지 검증한 뒤 읽음 포인터를 forward-only로 전진시키고 뱃지를 리셋한다.
 * 결과의 changed로 실제 전진 여부를 알려, 브로드캐스트(STOMP)는 호출 측(컨트롤러)이 담당한다.
 */
interface MarkMessagesAsReadUseCase {

	fun markAsRead(command: MarkMessagesAsReadCommand): MarkMessagesAsReadResult
}
