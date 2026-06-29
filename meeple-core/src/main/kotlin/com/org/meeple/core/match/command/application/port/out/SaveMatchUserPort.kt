package com.org.meeple.core.match.command.application.port.out

import com.org.meeple.core.match.command.domain.MatchUser
import java.time.LocalDateTime

/**
 * 매칭 읽기 모델(match_user) 저장 아웃포트.
 * user 프로필/상태 변경 이벤트에 반응해 매칭 가능 사용자를 적재(upsert)하거나, 로그인 시각만 갱신한다.
 */
interface SaveMatchUserPort {

	/** 매칭 가능 사용자를 적재한다. user_id 기준으로 없으면 INSERT, 있으면 UPDATE(upsert). */
	fun save(matchUser: MatchUser): MatchUser

	/** 이미 적재된 사용자의 마지막 로그인 시각만 갱신한다. 행이 없으면 아무 일도 하지 않는다. */
	fun updateLastLoginAt(userId: Long, lastLoginAt: LocalDateTime)
}
