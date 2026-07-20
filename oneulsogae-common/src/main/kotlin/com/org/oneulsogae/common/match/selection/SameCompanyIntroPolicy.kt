package com.org.oneulsogae.common.match.selection

/**
 * 같은 회사 구성원 소개 차단 정책(순수 로직). 모든 추천 경로(일일 배치·추가 소개·온보딩)가 이 기준을 공유한다.
 * 두 사람이 같은 회사이고 어느 한쪽이라도 거부하면 서로 소개하지 않는다(양방향).
 * 회사 미상(null)은 같은 회사로 보지 않으므로 차단하지 않는다.
 */
object SameCompanyIntroPolicy {

	/** [target]과 [candidate]의 소개가 같은 회사 거부로 차단되는지 판정한다. */
	fun blocked(
		targetCompanyName: String?,
		targetRefusesSameCompanyIntro: Boolean,
		candidateCompanyName: String?,
		candidateRefusesSameCompanyIntro: Boolean,
	): Boolean =
		targetCompanyName != null &&
			targetCompanyName == candidateCompanyName &&
			(targetRefusesSameCompanyIntro || candidateRefusesSameCompanyIntro)
}
