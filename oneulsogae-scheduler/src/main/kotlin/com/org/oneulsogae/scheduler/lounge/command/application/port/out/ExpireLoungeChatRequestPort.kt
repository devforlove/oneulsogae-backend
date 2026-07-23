package com.org.oneulsogae.scheduler.lounge.command.application.port.out

/**
 * 만료 대화 신청 1건을 정리(soft-delete + 절반 환불)하는 아웃포트. (신청 1건 = 트랜잭션 1개)
 * 실제 구현은 infra 어댑터가 core의 만료 처리 유스케이스에 위임한다. (scheduler는 core에 의존하지 않는다)
 */
interface ExpireLoungeChatRequestPort {

	/** 만료된 대화 신청([requestId])을 정리한다. */
	fun expire(requestId: Long)
}
