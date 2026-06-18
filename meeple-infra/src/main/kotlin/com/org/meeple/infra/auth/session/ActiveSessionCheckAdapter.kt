package com.org.meeple.infra.auth.session

import com.org.meeple.chatting.chat.application.port.out.CheckActiveSessionPort
import org.springframework.stereotype.Component

/**
 * chatting의 [CheckActiveSessionPort]를 [ActiveSessionStore]에 위임하는 어댑터.
 * chatting 모듈은 infra(Redis)에 의존하지 않으므로, 단일 활성 세션 대조를 out-port로 받아 여기서 채운다.
 * (HTTP의 TokenAuthenticationFilter가 ActiveSessionStore를 직접 쓰는 것과 같은 검사를 채팅에 제공한다)
 */
@Component
class ActiveSessionCheckAdapter(
	private val activeSessionStore: ActiveSessionStore,
) : CheckActiveSessionPort {

	override fun isActive(userId: Long, sessionId: String): Boolean =
		activeSessionStore.isActive(userId, sessionId)
}
