package com.org.oneulsogae.core.lounge.command.application.port.`in`

/**
 * 만료된 라운지 대화 신청 1건을 정리하는 유스케이스(in-port).
 * soft-delete와 신청자 코인 절반 환불을 한 트랜잭션으로 처리한다. 배치가 infra 브리지 어댑터를 거쳐 호출한다.
 * 구현은 [com.org.oneulsogae.core.lounge.command.application.ExpireLoungeChatRequestService].
 */
interface ExpireLoungeChatRequestUseCase {

	/** 만료된 대화 신청([requestId])을 정리한다. 없거나 만료가 아니면 아무것도 하지 않는다. */
	fun expire(requestId: Long)
}
