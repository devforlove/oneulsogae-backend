package com.org.oneulsogae.scheduler.lounge.command.application.port.out

import java.time.LocalDateTime

/**
 * 만료된(미수락) 라운지 대화 신청 id를 조회하는 아웃포트. (조회 전용)
 * 만료 = soft-delete 안 됨 + [now] 기준 만료 시각(expired_at) 경과 + PENDING 상태. (수락된 ACCEPTED는 만료되지 않는다)
 * 실제 구현(엔티티 조회)은 infra 어댑터가 담당한다. (scheduler는 core에 의존하지 않는다)
 */
interface GetExpiredLoungeChatRequestPort {

	/** 만료된 대화 신청 id 목록. */
	fun findExpiredRequestIds(now: LocalDateTime): List<Long>
}
