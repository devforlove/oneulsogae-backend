package com.org.oneulsogae.common.match

/** 2:2(팀) 매칭이 생성된 경로(유형). (온보딩 자동 소개는 1:1만 있으므로 팀 유형엔 없다) */
enum class TeamMatchType(val description: String) {

	/** 일일 매칭 배치로 생성된 팀 매칭. */
	DAILY("일일 팀 매칭"),

	/** 사용자 요청(필수 신청)으로 생성된 팀 매칭. */
	REQUIRED("요청 팀 매칭"),

	/** 팀 결성 시 구성원 개인 추천(recommended_teams)을 결성된 팀의 매칭으로 승격해 생성된 팀 매칭. */
	RECOMMENDED("추천 팀 매칭"),
}
