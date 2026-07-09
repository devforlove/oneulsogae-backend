package com.org.meeple.common.user

/** 사용자의 가입 진행 상태. */
enum class UserStatus {

	/** OAuth 인증만 완료한 상태. 아직 정식 가입(추가 정보 입력) 전. */
	ONBOARDING,

	/** 추가 정보 입력을 마치고 회사 이메일 인증을 진행 중인 상태. 인증 완료 전까지 정식 가입은 아니다. */
	EMAIL_VERIFICATION_PENDING,

	/** 회사 이메일 인증은 마쳤으나 도메인 매핑에서 회사명을 확정하지 못한 상태. 아직 정식 가입은 아니다. */
	COMPANY_NOT_RESOLVED,

	/** 정식 가입까지 완료한 활성 사용자. */
	ACTIVE,

	/** 탈퇴 유예 경과로 파기(익명화)된 종단 상태. 정식가입·매칭 대상이 아니다. */
	WITHDRAWN,

	;

	/** 정식 가입을 마친 상태인지 여부. */
	fun isRegistered(): Boolean = this == ACTIVE

	/**
	 * 매칭(1:1 소개·팀 추천) 대상이 되는 상태인지 여부.
	 * 회사 이메일 인증을 마친 사용자(ACTIVE·COMPANY_NOT_RESOLVED)는 회사명 확정 여부와 무관하게 매칭에 참여한다.
	 */
	fun isMatchable(): Boolean = this == ACTIVE || this == COMPANY_NOT_RESOLVED
}
