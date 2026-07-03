package com.org.meeple.common.match.selection

import java.time.LocalDateTime
import kotlin.random.Random

/**
 * 이상형·거리·최근 종합 점수로 후보를 정렬(동점군 무작위)하고 선택하는 순수 로직.
 * 일일 배치와 실시간 추가 소개가 공유한다. 거리 근접 순위(regionRankByRegionId)는
 * 호출부가 미리 계산해 넘긴다(배치=RegionProximityPort, 추가소개=GetRegionProximityPort).
 */
object MatchSelector {

	/**
	 * 후보를 종합 점수순(동점군 무작위)으로 정렬해 반환한다. (조회 상위 N 등 재소개 필터가 필요 없을 때)
	 * 같은 회사 소개 차단([SameCompanyIntroPolicy])·결혼 여부 절대 조건([MaritalStatusIntroPolicy])
	 * 대상 후보는 정렬 전에 제외한다.
	 */
	fun <T : ScoringCandidate> orderByScore(
		targetProfile: MatchScoringProfile?,
		targetCompanyName: String?,
		targetRefusesSameCompanyIntro: Boolean,
		candidates: List<T>,
		profileOf: (T) -> MatchScoringProfile?,
		regionRankByRegionId: Map<Long, Int>,
		regionCount: Int,
		now: LocalDateTime,
		loginAfter: LocalDateTime,
		random: Random,
	): List<T> {
		val allowed: List<T> = candidates.filterNot { candidate: T ->
			SameCompanyIntroPolicy.blocked(
				targetCompanyName = targetCompanyName,
				targetRefusesSameCompanyIntro = targetRefusesSameCompanyIntro,
				candidateCompanyName = candidate.companyName,
				candidateRefusesSameCompanyIntro = candidate.refuseSameCompanyIntro,
			) || MaritalStatusIntroPolicy.blocked(targetProfile, profileOf(candidate))
		}
		val scored: List<Pair<T, Double>> = allowed.map { candidate: T ->
			candidate to score(candidate, targetProfile, profileOf, regionRankByRegionId, regionCount, now, loginAfter)
		}
		return MatchScorer.orderByScore(scored, random)
	}

	/**
	 * 정렬된 후보 중 [isExcluded]가 false인 최고점 후보 1명을 반환한다. 없으면 null.
	 * (재소개 이력이 있는 후보를 건너뛴다. 같은 회사 소개 차단·결혼 여부 절대 조건 후보는 [orderByScore]에서 이미 제외된다)
	 */
	fun <T : ScoringCandidate> selectBest(
		targetProfile: MatchScoringProfile?,
		targetCompanyName: String?,
		targetRefusesSameCompanyIntro: Boolean,
		candidates: List<T>,
		profileOf: (T) -> MatchScoringProfile?,
		regionRankByRegionId: Map<Long, Int>,
		regionCount: Int,
		now: LocalDateTime,
		loginAfter: LocalDateTime,
		random: Random,
		isExcluded: (T) -> Boolean,
	): T? =
		orderByScore(targetProfile, targetCompanyName, targetRefusesSameCompanyIntro, candidates, profileOf, regionRankByRegionId, regionCount, now, loginAfter, random)
			.firstOrNull { candidate: T -> !isExcluded(candidate) }

	private fun <T : ScoringCandidate> score(
		candidate: T,
		targetProfile: MatchScoringProfile?,
		profileOf: (T) -> MatchScoringProfile?,
		regionRankByRegionId: Map<Long, Int>,
		regionCount: Int,
		now: LocalDateTime,
		loginAfter: LocalDateTime,
	): Double {
		val idealFit: Double = MatchScorer.mutualIdealFit(targetProfile, profileOf(candidate))
		val distanceScore: Double = MatchScorer.distanceScore(regionRankByRegionId[candidate.regionId], regionCount)
		val recencyScore: Double = MatchScorer.recencyScore(candidate.lastLoginAt, loginAfter, now)
		return MatchScorer.combinedScore(idealFit, distanceScore, recencyScore)
	}
}
