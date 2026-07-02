package com.org.meeple.core.solomatch.query.dto

/**
 * 매칭 목록 조회 결과([MatchWithPartner])의 일급 컬렉션.
 * 목록 노출 순서 규칙을 캡슐화해, 조회 서비스가 정렬 로직을 인라인하지 않게 한다.
 */
data class MatchesWithPartner(
	private val values: List<MatchWithPartner>,
) {

	/**
	 * 목록 노출 순서로 정렬한 매칭 목록.
	 * 성사(MATCHED) → 상대 수락 대기(PARTIALLY_ACCEPTED) → 소개됨(PROPOSED) 순으로 노출하고,
	 * 같은 상태 안에서는 최신(matchId 내림차순)순으로 정렬한다.
	 */
	fun sortedForDisplay(): List<MatchWithPartner> =
		values.sortedWith(
			compareBy<MatchWithPartner> { match: MatchWithPartner -> match.status.listPriority }
				.thenByDescending { match: MatchWithPartner -> match.matchId },
		)
}
