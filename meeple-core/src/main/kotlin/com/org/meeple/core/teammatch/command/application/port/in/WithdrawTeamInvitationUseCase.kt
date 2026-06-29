package com.org.meeple.core.teammatch.command.application.port.`in`

import com.org.meeple.core.teammatch.command.domain.Team

/**
 * 팀 초대 철회 유스케이스(인포트). 초대 단계(INVITING)의 거절(초대받은 사람)·취소(초대자)를 함께 처리한다.
 * 결과로 팀이 비활성화(DEACTIVATED)되고 소프트 삭제된다.
 */
interface WithdrawTeamInvitationUseCase {

	/** [userId]가 [teamId] 팀의 초대를 철회(거절/취소)하고, 비활성화된 팀을 반환한다. */
	fun withdraw(userId: Long, teamId: Long): Team
}
