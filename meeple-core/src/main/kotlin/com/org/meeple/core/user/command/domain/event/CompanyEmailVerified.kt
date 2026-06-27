package com.org.meeple.core.user.command.domain.event

/**
 * 회사 이메일 인증으로 온보딩(정식 가입)이 막 완료됐을 때 발행되는 이벤트.
 * [com.org.meeple.core.user.command.application.UserEventHandler]가 커밋 이후(AFTER_COMMIT) 받아,
 * 매칭 읽기 모델(match_user) 동기화가 끝난 뒤 첫 매칭 자동 소개로 이어간다.
 */
data class CompanyEmailVerified(
	val userId: Long,
)
