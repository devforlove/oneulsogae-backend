package com.org.oneulsogae.core.solomatch.command.application.port.out

import com.org.oneulsogae.core.solomatch.command.domain.Match
import java.time.LocalDateTime

/**
 * 매칭 저장 아웃포트.
 * 신규 소개를 저장하거나, 기존 매칭(id 존재)의 응답/상태 변경분을 반영한다.
 */
interface SaveMatchPort {

	fun save(match: Match): Match

	/**
	 * 참가자의 매칭 확인 시각을 아직 미기록(null)인 경우에만 기록한다.
	 * 갱신된 행 수를 반환한다(이미 기록됐으면 0 — 동시 조회 중복 알람 방지의 판정 기준).
	 */
	fun markMemberCheckedIfUnchecked(matchId: Long, userId: Long, checkedAt: LocalDateTime): Int
}
