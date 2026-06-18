package com.org.meeple.scheduler.match.command.application.port.out

import java.time.LocalDateTime

/**
 * 만료된 소개의 식별자를 조회하는 아웃포트.
 * 아직 성사/종료되지 않은(응답 대기) 소개 중 [now] 기준 만료 시각이 지난 매칭 id를 가져온다.
 * 실제 구현은 infra 어댑터가 담당한다. (scheduler는 core·영속성에 의존하지 않는다)
 */
interface GetExpiredMatchIdsPort {

	fun findExpiredMatchIds(now: LocalDateTime): List<Long>
}
