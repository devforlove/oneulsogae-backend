package com.org.meeple.common.match.selection

import com.org.meeple.common.user.MaritalStatus

/**
 * 이상형 결혼 여부(미혼/돌싱) 절대 조건 정책(순수 로직). 다른 이상형 조건과 달리 점수(우선순위)가 아니라
 * 필터로 적용된다 — 어느 한쪽이라도 결혼 여부 이상형을 지정했는데 상대가 부합하지 않으면 소개하지 않는다(양방향).
 * 조건을 지정했는데 상대의 결혼 여부를 알 수 없으면(프로필/속성 null) 부합을 확인할 수 없으므로 차단한다.
 */
object MaritalStatusIntroPolicy {

	/** [target]과 [candidate]의 소개가 결혼 여부 절대 조건으로 차단되는지 판정한다. */
	fun blocked(target: MatchScoringProfile?, candidate: MatchScoringProfile?): Boolean =
		directionBlocked(target, candidate) || directionBlocked(candidate, target)

	/** [preference]가 지정한 결혼 여부 이상형을 [other]가 충족하지 못하는지(한 방향) 판정한다. */
	private fun directionBlocked(preference: MatchScoringProfile?, other: MatchScoringProfile?): Boolean {
		val required: MaritalStatus = preference?.idealMaritalStatus ?: return false
		return other?.maritalStatus != required
	}
}
