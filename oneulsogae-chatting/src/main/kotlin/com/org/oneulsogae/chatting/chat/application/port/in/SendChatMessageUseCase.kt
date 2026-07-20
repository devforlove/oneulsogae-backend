package com.org.oneulsogae.chatting.chat.application.port.`in`

import com.org.oneulsogae.chatting.chat.application.port.`in`.command.SendChatMessageCommand
import com.org.oneulsogae.chatting.chat.application.port.`in`.result.SentChatMessageResult

/**
 * 채팅 메세지 발송 인포트(유스케이스).
 * 발신자가 그 방의 참가자인지 검증하고, 메세지를 저장한 뒤 방 공통 마지막 메세지·상대방 안 읽은 개수를 갱신한다.
 * 저장 결과를 [SentChatMessageResult](도메인이 아닌 리드 모델)로 반환하며, 구독자 브로드캐스트(STOMP)는 호출 측(컨트롤러)이 담당한다.
 */
interface SendChatMessageUseCase {

	fun send(command: SendChatMessageCommand): SentChatMessageResult
}
