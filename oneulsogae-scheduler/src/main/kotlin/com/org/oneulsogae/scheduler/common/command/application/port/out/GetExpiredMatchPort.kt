package com.org.oneulsogae.scheduler.common.command.application.port.out

import java.time.LocalDateTime

/**
 * 만료된 매칭·라운지 대화 신청 id를 조회하는 아웃포트. (조회 전용)
 * 만료 = soft-delete 안 됨 + [now] 기준 만료 시각 경과 + 성사/수락되지 않은 상태.
 * (매칭은 PROPOSED/PARTIALLY_ACCEPTED, 대화 신청은 PENDING)
 * 실제 구현(엔티티 조회)은 infra 어댑터가 담당한다. (scheduler는 core에 의존하지 않는다)
 */
interface GetExpiredMatchPort {

	/** 만료된 솔로 매칭 id 목록. */
	fun findExpiredSoloMatchIds(now: LocalDateTime): List<Long>

	/** 만료된 팀 매칭 id 목록. */
	fun findExpiredTeamMatchIds(now: LocalDateTime): List<Long>

	/** 만료된 라운지 대화 신청 id 목록. */
	fun findExpiredLoungeChatRequestIds(now: LocalDateTime): List<Long>
}
