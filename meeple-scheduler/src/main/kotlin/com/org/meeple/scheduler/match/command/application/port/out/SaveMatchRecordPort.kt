package com.org.meeple.scheduler.match.command.application.port.out

import com.org.meeple.common.user.Gender
import java.time.LocalDateTime

/**
 * 배치가 매칭(소개) 이력을 기록하기 위한 아웃포트. (기록 전용 — 조회는 [com.org.meeple.scheduler.match.query.dao.MatchRecordDao]가 담당)
 * 매칭 도메인/영속성은 core·infra가 갖고 있으므로, scheduler는 자기 관점의 이 포트만 정의하고
 * 실제 구현(core의 Match·매칭 포트 위임)은 infra 어댑터가 담당한다. (scheduler는 core에 의존하지 않는다)
 */
interface SaveMatchRecordPort {

	/**
	 * 신규 소개(PROPOSED) 매칭을 저장한다.
	 * 요청자([requesterId])의 성별([requesterGender])로 남/녀 자리를 배치하고, [now] 기준으로 소개 일자·만료 시각을 잡는다.
	 */
	fun saveProposedMatch(requesterId: Long, requesterGender: Gender, partnerId: Long, now: LocalDateTime)
}
