package com.org.meeple.scheduler.match.command.application.port.out

/**
 * 매칭(소개)을 제거하는 아웃포트.
 * 매칭 제거 로직(소프트 삭제 + 코인 환불)은 core가 갖고 있으므로, scheduler는 자기 관점의 이 포트만 정의하고
 * 실제 구현(core의 RemoveMatchUseCase 위임)은 infra 어댑터가 담당한다. (scheduler는 core에 의존하지 않는다)
 */
interface RemoveMatchPort {

	/** [matchId] 매칭을 제거한다. 이미 없으면 멱등 no-op이다. */
	fun remove(matchId: Long)
}
