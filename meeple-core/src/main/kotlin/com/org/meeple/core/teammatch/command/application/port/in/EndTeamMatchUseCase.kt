package com.org.meeple.core.teammatch.command.application.port.`in`

/**
 * 팀 매칭 종료 인포트(유스케이스).
 * 성사(MATCHED)된 팀 매칭에서 [userId]가 속한 팀이 매칭을 종료한다.
 * 참가·성사 검증 → 내 팀 비활성/soft delete + 우리 팀원 채팅 비활성 + 상대 팀 알림.
 */
interface EndTeamMatchUseCase {

	fun endTeamMatch(userId: Long, teamMatchId: Long)
}
