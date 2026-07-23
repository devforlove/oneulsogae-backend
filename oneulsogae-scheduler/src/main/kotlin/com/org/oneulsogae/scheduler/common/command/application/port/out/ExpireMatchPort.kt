package com.org.oneulsogae.scheduler.common.command.application.port.out

/**
 * 만료 매칭·라운지 대화 신청 1건을 정리(soft-delete + 환불 등)하는 아웃포트. (1건 = 트랜잭션 1개)
 * 실제 구현은 infra 어댑터가 core의 만료 처리 유스케이스에 위임한다. (scheduler는 core에 의존하지 않는다)
 */
interface ExpireMatchPort {

	/** 만료된 솔로 매칭([matchId])을 정리한다. */
	fun expireSoloMatch(matchId: Long)

	/** 만료된 팀 매칭([teamMatchId])을 정리한다. */
	fun expireTeamMatch(teamMatchId: Long)

	/** 만료된 라운지 대화 신청([requestId])을 정리한다. (soft-delete + 신청 코인 절반 환불) */
	fun expireLoungeChatRequest(requestId: Long)
}
