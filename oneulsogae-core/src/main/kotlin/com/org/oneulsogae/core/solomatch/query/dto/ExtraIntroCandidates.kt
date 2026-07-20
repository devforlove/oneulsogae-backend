package com.org.oneulsogae.core.solomatch.query.dto

/** 추가 소개 후보 조회 결과. [totalCount]는 전체 자격 후보 수, [candidates]는 점수 상위 표시 목록. */
data class ExtraIntroCandidates(
	val totalCount: Int,
	val candidates: List<ExtraIntroCandidate>,
)
