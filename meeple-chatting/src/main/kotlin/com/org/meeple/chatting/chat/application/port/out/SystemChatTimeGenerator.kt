package com.org.meeple.chatting.chat.application.port.out

import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 시스템 시계 기반 [TimeGenerator] 구현. (chatting 소유)
 * core의 SystemTimeGenerator·scheduler의 SystemBatchTimeGenerator와 빈 이름이 겹치지 않게 클래스명을 구분한다.
 */
@Component
class SystemChatTimeGenerator : TimeGenerator {

	override fun now(): LocalDateTime = LocalDateTime.now()
}
