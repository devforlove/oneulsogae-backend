package com.org.oneulsogae.core.solomatch.query.dto

/**
 * 내 매칭 목록 화면의 조회 결과(read model).
 * 목록 자체([matches])와 함께 조회한 사용자의 회사 인증 여부([companyVerified])를 담는다.
 * (회사 인증을 마친 사용자만 소개 기능을 쓸 수 있어, 프론트엔드가 이 플래그로 화면을 분기한다)
 */
data class MyMatches(
	/** 조회한 사용자가 회사 인증을 마쳤는지 여부. */
	val companyVerified: Boolean,
	/** 노출 순서(상태 우선순위 → 최신순)로 정렬된 매칭 목록. 없으면 빈 리스트. */
	val matches: List<MatchWithPartner>,
)
