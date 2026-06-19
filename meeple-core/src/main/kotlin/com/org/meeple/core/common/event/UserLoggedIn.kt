package com.org.meeple.core.common.event

import java.time.LocalDateTime

/**
 * 사용자가 로그인해 마지막 로그인 시각이 갱신됐을 때 발행되는 크로스 도메인 이벤트.
 * match 도메인은 이미 match_user에 행이 있는 사용자에 한해 [lastLoginAt]만 갱신한다. (행이 없으면 무시)
 * 매칭 가능 스냅샷 전체를 다시 싣지 않아 매 로그인의 동기화 비용을 최소화한다.
 */
data class UserLoggedIn(
	val userId: Long,
	val lastLoginAt: LocalDateTime,
)
