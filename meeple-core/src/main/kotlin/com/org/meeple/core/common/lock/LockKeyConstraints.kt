package com.org.meeple.core.common.lock

/**
 * 분산 락 키 접두사 상수 모음.
 * 도메인/연산별 접두사를 여기에 상수로 정의하고, [DistributedLock.prefix]에 지정한다.
 * 최종 키는 "{prefix}{DELIMITER}{key1}{DELIMITER}{key2}..." 형태가 된다. ([DistributedLock.keys]로 userId·matchId 등 지정)
 * (예: MATCH_INTEREST::42, MATCH_INTEREST::42::FREE)
 */
object LockKeyConstraints {

	/** 접두사와 키 구성요소(userId·matchId 등) 사이 구분자. */
	const val DELIMITER: String = "::"

	/**
	 * 매칭 관심(신청/수락) 처리 락. matchId로 잠가 같은 매칭의 상태 변경을 직렬화한다.
	 * 관심 보내기(SendInterestService, 신청·수락 통합)에서 두 참가자 동시 요청이나 더블클릭으로 인한
	 * lost update·코인 이중 차감을 막는다.
	 */
	const val MATCH_INTEREST: String = "MATCH_INTEREST"

	/**
	 * 팀 결성 라이프사이클(수락/철회/해체) 처리 락. teamId로 잠가 한 팀의 상태 변경을 직렬화한다.
	 * 수락(invited)과 초대취소(owner) 동시 요청으로 인한 ACTIVE↔DEACTIVATED lost update를 막는다.
	 */
	const val TEAM_LIFECYCLE: String = "TEAM_LIFECYCLE"
}
