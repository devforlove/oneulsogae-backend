package com.org.meeple.scheduler.solomatch.command.domain

import com.org.meeple.scheduler.solomatch.query.dto.MatchScoringProfile

/**
 * 일일 매칭의 이상형 우선순위 점수 계산기(순수). 이상형은 필터가 아니라 우선순위이므로,
 * 부합하지 않아도 점수만 낮아진다. 이상형 부합(양방향)·거리·최근을 0~1로 정규화해 가중 합산한다.
 */
object MatchScorer {

	/**
	 * 두 프로필의 이상형 부합도(0~1, 양방향 평균).
	 * 각 방향은 "지정한 이상형 조건 중 상대 속성이 충족하는 비율"이며, 지정 조건이 없으면 1.0(선호 없음).
	 */
	fun mutualIdealFit(target: MatchScoringProfile?, candidate: MatchScoringProfile?): Double =
		(directionFit(target, candidate) + directionFit(candidate, target)) / 2.0

	/** [preference]의 이상형으로 [other]의 속성을 평가한 한 방향 부합도(0~1). 지정 조건이 없으면 1.0. */
	private fun directionFit(preference: MatchScoringProfile?, other: MatchScoringProfile?): Double {
		if (preference == null) return 1.0
		val results: List<Boolean> = buildList {
			if (preference.idealAgeMin != null && preference.idealAgeMax != null) {
				val age: Int? = other?.age
				add(age != null && age in preference.idealAgeMin..preference.idealAgeMax)
			}
			if (preference.idealHeightMin != null && preference.idealHeightMax != null) {
				val height: Int? = other?.height
				add(height != null && height in preference.idealHeightMin..preference.idealHeightMax)
			}
			if (preference.idealMaritalStatus != null) {
				add(other?.maritalStatus == preference.idealMaritalStatus)
			}
			if (preference.idealSmokingStatus != null) {
				add(other?.smokingStatus == preference.idealSmokingStatus)
			}
			if (preference.idealDrinkingStatus != null) {
				add(other?.drinkingStatus == preference.idealDrinkingStatus)
			}
			if (preference.idealReligion != null) {
				add(other?.religion == preference.idealReligion)
			}
		}
		if (results.isEmpty()) return 1.0
		return results.count { satisfied: Boolean -> satisfied }.toDouble() / results.size
	}
}
