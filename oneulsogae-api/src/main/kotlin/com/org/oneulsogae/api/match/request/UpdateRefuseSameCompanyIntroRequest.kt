package com.org.oneulsogae.api.match.request

/**
 * 같은 회사 구성원 소개 거부 플래그 변경 요청 본문.
 * true면 같은 회사 구성원에게 소개(추천)되지 않는다. (필드 누락 시 400)
 */
data class UpdateRefuseSameCompanyIntroRequest(
	val refuseSameCompanyIntro: Boolean,
)
