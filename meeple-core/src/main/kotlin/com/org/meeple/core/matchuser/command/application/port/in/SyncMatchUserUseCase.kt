package com.org.meeple.core.matchuser.command.application.port.`in`

import com.org.meeple.core.common.event.MatchProfileSnapshot
import java.time.LocalDateTime

/**
 * 매칭 읽기 모델(match_user) 동기화 인포트.
 * 매칭 가능 스냅샷은 user 도메인이 자기 데이터로 만들어 넘기고(match는 user로 콜백하지 않는다), match 도메인이 자기 읽기 모델에 반영한다.
 */
interface SyncMatchUserUseCase {

	/** 매칭 가능 스냅샷이 있으면 적재(upsert), 없으면(매칭 불가) 후보 풀에서 제거한다. */
	fun sync(userId: Long, snapshot: MatchProfileSnapshot?)

	/** 이미 적재된 사용자의 마지막 로그인 시각만 갱신한다. (미적재 사용자는 갱신 대상이 없어 무시) */
	fun updateLastLogin(userId: Long, lastLoginAt: LocalDateTime)
}
