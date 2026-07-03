package com.org.meeple.common.match.selection

import java.time.Duration
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * 매칭의 이상형 우선순위 점수 계산기(순수). 이상형은 필터가 아니라 우선순위이므로,
 * 부합하지 않아도 점수만 낮아진다. 이상형 부합(양방향)·거리·최근을 0~1로 정규화해 가중 합산한다.
 * 일일 배치와 실시간 추가 소개가 공유한다.
 * (단, 결혼 여부(미혼/돌싱) 이상형은 점수가 아니라 절대 조건 — [MaritalStatusIntroPolicy]가 필터로 처리한다)
 */
object MatchScorer {

	private const val IDEAL_WEIGHT: Double = 0.4
	private const val DISTANCE_WEIGHT: Double = 0.4
	private const val RECENCY_WEIGHT: Double = 0.2

	/** 종합 점수 버킷 크기. 같은 버킷(≈동점)은 무작위로 섞는다. */
	private const val BUCKET_SIZE: Double = 0.05

	/**
	 * 두 프로필의 이상형 부합도(0~1, 양방향 평균).
	 * 각 방향은 "지정한 이상형 조건 중 상대 속성이 충족하는 비율"이며, 지정 조건이 없으면 1.0(선호 없음).
	 */
	fun mutualIdealFit(target: MatchScoringProfile?, candidate: MatchScoringProfile?): Double =
		(directionFit(target, candidate) + directionFit(candidate, target)) / 2.0

	/** 근접 순위 [rank](같은 지역=0, 없으면 null)를 0~1 점수로. 같은 지역=1.0, 가장 먼 지역=0.0, 목록 밖=0.0. */
	fun distanceScore(rank: Int?, regionCount: Int): Double {
		if (rank == null) return 0.0
		if (regionCount <= 1) return 1.0
		return 1.0 - rank.toDouble() / (regionCount - 1)
	}

	/** 2주 창(loginAfter~now)에서 [lastLoginAt]의 최근성 0~1. now=1.0, 창 시작=0.0. */
	fun recencyScore(lastLoginAt: LocalDateTime, loginAfter: LocalDateTime, now: LocalDateTime): Double {
		val windowSeconds: Long = Duration.between(loginAfter, now).seconds
		if (windowSeconds <= 0) return 1.0
		val elapsedSeconds: Long = Duration.between(loginAfter, lastLoginAt).seconds
		return (elapsedSeconds.toDouble() / windowSeconds).coerceIn(0.0, 1.0)
	}

	/** 세 요소를 가중 합산한 종합 점수(0~1). 이상형 0.4 / 거리 0.4 / 최근 0.2. */
	fun combinedScore(idealFit: Double, distanceScore: Double, recencyScore: Double): Double =
		IDEAL_WEIGHT * idealFit + DISTANCE_WEIGHT * distanceScore + RECENCY_WEIGHT * recencyScore

	/**
	 * 후보를 종합 점수 내림차순으로 정렬하되, [BUCKET_SIZE] 단위 버킷 안은 [random]으로 섞는다.
	 * (상위 동점군 내 무작위 — 같은 상대만 반복 노출되지 않게 하면서 이상형 우선순위는 유지)
	 */
	fun <T> orderByScore(scored: List<Pair<T, Double>>, random: Random): List<T> =
		scored
			.groupBy { (_, score: Double) -> (score / BUCKET_SIZE).toInt() }
			.entries
			.sortedByDescending { entry: Map.Entry<Int, List<Pair<T, Double>>> -> entry.key }
			.flatMap { entry: Map.Entry<Int, List<Pair<T, Double>>> ->
				entry.value.shuffled(random).map { (item: T, _) -> item }
			}

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
