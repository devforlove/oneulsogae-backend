package com.org.oneulsogae.core.solomatch.command.application.port.`in`

/**
 * 만료된(미성사) 솔로 매칭 1건을 정리하는 유스케이스(in-port). (매치 1건 = 트랜잭션 1개)
 * 매칭을 soft-delete하고, 한쪽만 신청(APPLY)해 성사되지 못한 경우 신청자에게 신청 비용의 절반을 환불하며 환불 팝업을 만든다.
 * 만료 대상 선별(성사·종료·미만료 제외)은 호출 측(배치 조회)이 책임진다.
 */
interface ExpireSoloMatchUseCase {

	/** 만료된 솔로 매칭([matchId])을 정리한다. (없으면 무시) */
	fun expireSoloMatch(matchId: Long)
}
