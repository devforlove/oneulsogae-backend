package com.org.meeple.core.teammatch.query.service.port.`in`

import com.org.meeple.core.teammatch.query.dto.SentInvitation

/**
 * 내가 보낸 초대 현황을 조회하는 유스케이스(인포트).
 * 요청자가 ACTIVE 구성원(=초대자)인 INVITING 팀 중 가장 최근 1건을 반환한다. 없으면 null.
 */
interface GetSentInvitationUseCase {

	/** [userId]가 보낸 가장 최근 INVITING 초대 현황을 조회한다. 없으면 null. */
	fun get(userId: Long): SentInvitation?
}
