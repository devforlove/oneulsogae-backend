package com.org.meeple.core.user.command.domain.event

/**
 * 사용자의 프로필/가입 상태가 바뀌었을 때 user 도메인 내부에서 발행되는 이벤트.
 * 프로필 입력·편집, 회사 이메일 인증/회사명 확정(가입 상태 전이) 시 command 서비스가 발행한다.
 * [com.org.meeple.core.user.command.application.UserEventHandler]가 받아 매칭 동기화용 스냅샷을 만들어 match 도메인에 전달한다.
 */
data class UserProfileChanged(
	val userId: Long,
)
