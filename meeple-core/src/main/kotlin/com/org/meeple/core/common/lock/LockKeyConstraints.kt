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
	 * 매칭 상태 변경(관심 신청/수락·종료) 처리 락. matchId로 잠가 같은 매칭의 상태 변경을 직렬화한다.
	 * 관심 보내기(SendInterestService, 신청·수락 통합)와 매칭 종료(EndMatchService)가 같은 키를 공유해,
	 * 두 참가자 동시 요청·더블클릭이나 종료와 관심이 같은 매칭 행에 동시에 쓰여 발생하는
	 * lost update(예: 종료 직후 관심으로 매칭이 되살아남)·코인 이중 차감을 막는다.
	 */
	const val MATCH_INTEREST: String = "MATCH_INTEREST"

	/**
	 * 팀 결성 라이프사이클(철회/해체/수정) 처리 락. teamId로 잠가 한 팀의 상태 변경을 직렬화한다.
	 * 같은 팀에 대한 철회·해체·수정 동시 요청으로 인한 lost update를 막는다.
	 * (수락은 사용자 단위 직렬화가 필요해 [TEAM_MEMBERSHIP]을 쓰고, 같은 팀의 수락↔철회 경합은 teams 행의 낙관적 락(@Version)으로 막는다)
	 */
	const val TEAM_LIFECYCLE: String = "TEAM_LIFECYCLE"

	/**
	 * 팀 소속(초대 생성/수락) 처리 락. userId로 잠가 한 사용자의 활성 팀 합류를 직렬화한다.
	 * "한 사용자는 활성 팀 하나"는 사용자 단위 불변식이라 teamId가 아니라 userId(초대자=ownerId, 수락자=userId)로 잠근다.
	 * 동시 초대(owner 중복 생성)·동시 수락(서로 다른 두 팀 수락)·초대와 수락 동시 요청으로 두 활성 팀에 소속되는 것을 막는다.
	 * (teamId 키와 섞이지 않도록 [TEAM_LIFECYCLE]과 다른 접두사를 쓴다)
	 */
	const val TEAM_MEMBERSHIP: String = "TEAM_MEMBERSHIP"

	/**
	 * 팀 매칭 관심(신청/수락) 처리 락. teamMatchId로 잠가 같은 팀 매칭의 상태 변경을 직렬화한다.
	 * 두 팀이 공유하는 "팀 매칭"이 경합 대상이므로 userId/teamId가 아니라 teamMatchId로 잠근다.
	 * 동시 요청·더블클릭으로 인한 lost update·코인 이중 차감을 막는다. (waitTime=0이면 겹친 요청은 즉시 실패)
	 */
	const val TEAM_MATCH_INTEREST: String = "TEAM_MATCH_INTEREST"
}
