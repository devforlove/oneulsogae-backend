package com.org.oneulsogae.core.common.event

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

/**
 * Spring 애플리케이션 이벤트 버스를 사용하는 [DomainEventPublisher] 기본 구현.
 * 인프로세스 발행이므로 [SystemTimeGenerator]와 같이 core에 둔다.
 */
@Component
class SpringDomainEventPublisher(
	private val applicationEventPublisher: ApplicationEventPublisher,
) : DomainEventPublisher {

	override fun publish(event: Any) {
		applicationEventPublisher.publishEvent(event)
	}
}
