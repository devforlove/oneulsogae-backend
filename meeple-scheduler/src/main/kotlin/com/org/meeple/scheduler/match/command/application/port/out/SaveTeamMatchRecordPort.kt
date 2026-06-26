package com.org.meeple.scheduler.match.command.application.port.out

import java.time.LocalDateTime

/**
 * 배치가 팀 매칭(소개) 이력을 기록하기 위한 아웃포트. (기록 전용 — 조회는 [com.org.meeple.scheduler.match.query.dao.GetTeamMatchRecordDao]가 담당)
 * 팀 매칭 도메인/영속성은 core·infra가 갖고 있으므로, scheduler는 자기 관점의 이 포트만 정의하고
 * 실제 구현(core의 TeamMatch·팀 매칭 포트 위임)은 infra 어댑터가 담당한다. (scheduler는 core에 의존하지 않는다)
 */
interface SaveTeamMatchRecordPort {

	/** 두 팀([teamAId], [teamBId])의 신규 소개(PROPOSED) 팀 매칭을 저장한다. [now] 기준으로 소개 일자·만료 시각을 잡는다. */
	fun saveProposedTeamMatch(teamAId: Long, teamBId: Long, now: LocalDateTime)
}
