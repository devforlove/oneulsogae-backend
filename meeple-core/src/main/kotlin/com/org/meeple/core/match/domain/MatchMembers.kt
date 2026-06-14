package com.org.meeple.core.match.domain

import com.org.meeple.common.user.Gender

/**
 * 한 매칭의 참가자([MatchMember]) 목록의 일급 컬렉션(first-class collection).
 * 1:1이면 두 명(남1·여1)을 담고, N:N으로 확장하면 여러 명을 담는다.
 * 참가자 관련 동작(파생·개수 등)을 한곳에 응집한다.
 */
data class MatchMembers(
	val values: List<MatchMember>,
) {

	/** 참가자 수. */
	val size: Int
		get() = values.size

	/** 비어 있는지 여부. */
	fun isEmpty(): Boolean = values.isEmpty()

	companion object {

		/**
		 * 1:1 매칭([match])의 남/녀를 각각 참가자로 펼친다. (male→MALE, female→FEMALE)
		 * 매칭 생성 시 정규화 참가자 테이블에 함께 기록하기 위한 파생이다. (확장 씨앗)
		 */
		fun from(match: Match): MatchMembers =
			MatchMembers(
				listOf(
					MatchMember(matchId = match.id, userId = match.maleUserId, gender = Gender.MALE),
					MatchMember(matchId = match.id, userId = match.femaleUserId, gender = Gender.FEMALE),
				),
			)
	}
}
