package com.org.oneulsogae.chatting.chat.application.port.out

import java.time.LocalDateTime

/**
 * 현재 시각 제공 추상화. (chatting 소유)
 * chatting은 core에 의존하지 않으므로 자체 시각 포트를 둔다. (테스트에서 시각 고정/대체 가능)
 */
interface TimeGenerator {

	fun now(): LocalDateTime
}
