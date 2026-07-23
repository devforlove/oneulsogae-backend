package com.org.oneulsogae.core.solomatch.query.dto

/**
 * 추가 소개 후보 조회 결과. [totalCount]는 전체 자격 후보 수, [candidates]는 점수 상위 표시 목록.
 * [companyVerified]는 조회한 사용자의 회사 인증 여부 — 회사 인증을 마친 사용자만 추가 소개를 받을 수 있어,
 * 프론트엔드가 이 플래그로 코인 차감 시도 전에 인증 안내로 분기한다. (MyMatches와 같은 관례)
 */
data class ExtraIntroCandidates(
	val totalCount: Int,
	val candidates: List<ExtraIntroCandidate>,
	/** 조회한 사용자가 회사 인증을 마쳤는지 여부. */
	val companyVerified: Boolean,
)
