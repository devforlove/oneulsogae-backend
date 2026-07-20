package com.org.oneulsogae.common.match

/** 1:1(솔로) 매칭(소개)이 생성된 경로(유형). */
enum class SoloMatchType(val description: String) {

	/** 일일 매칭 배치로 생성된 소개. */
	DAILY("일일 매칭"),

	/** 온보딩(가입 직후) 자동 소개. */
	ONBOARDING("온보딩 매칭"),

	/** 사용자 요청(필수 신청)으로 생성된 소개. */
	REQUIRED("요청 매칭"),

	/** 사용자가 코인으로 추가 소개받아 생성된 소개. */
	EXTRA("추가 소개"),
}
