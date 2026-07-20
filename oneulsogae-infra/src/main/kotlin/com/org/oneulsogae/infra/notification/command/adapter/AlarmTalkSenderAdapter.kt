package com.org.oneulsogae.infra.notification.command.adapter

import com.org.oneulsogae.core.notification.command.application.port.out.AlarmTalkSenderPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * [AlarmTalkSenderPort] stub 구현. 지금은 전송 의도만 로그로 남긴다.
 * 카카오 알림톡 연동 시 이 클래스만 실제 API 호출로 교체한다(게이트·포트 불변).
 */
@Component
class AlarmTalkSenderAdapter : AlarmTalkSenderPort {

	private val log = LoggerFactory.getLogger(javaClass)

	override fun send(userId: Long, title: String, body: String) {
		log.info("[알림톡 stub] userId={} title={} body={}", userId, title, body)
	}
}
