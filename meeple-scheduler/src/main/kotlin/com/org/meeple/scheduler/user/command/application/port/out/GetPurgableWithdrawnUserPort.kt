package com.org.meeple.scheduler.user.command.application.port.out

import java.time.LocalDateTime

/** 파기 대상(유예 경과·미파기) 사용자 id를 조회하는 아웃포트. (구현은 infra, 소프트삭제 행이라 네이티브) */
interface GetPurgableWithdrawnUserPort {

	/** deleted_at이 [cutoff] 이전이고 아직 익명화(WITHDRAWN)되지 않은 사용자 id 목록. */
	fun findUserIdsWithdrawnBefore(cutoff: LocalDateTime): List<Long>
}
