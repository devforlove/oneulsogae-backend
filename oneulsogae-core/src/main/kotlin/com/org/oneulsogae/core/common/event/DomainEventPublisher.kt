package com.org.oneulsogae.core.common.event

/**
 * 도메인 이벤트 발행 추상화.
 * application 서비스가 Spring 이벤트 발행기에 직접 의존하지 않고 이 인터페이스에 의존하게 하여,
 * 테스트에서 발행기를 fake로 대체하거나 발행 사실을 검증할 수 있도록 한다.
 * (도메인 모델이 아니라 application 서비스에서 상태 변경 후 발행한다)
 */
interface DomainEventPublisher {

	/** 도메인 이벤트를 발행한다. 같은 트랜잭션 안에서 호출하고, 수신측은 커밋 이후 반응하도록 둔다. */
	fun publish(event: Any)
}
