package com.org.meeple.core.teammatch.query.service.port.`in`

import com.org.meeple.core.teammatch.query.dto.ReceivedInvitation

/**
 * 내가 받은 초대 리스트를 조회하는 유스케이스(인포트).
 * 요청자가 INVITED 구성원인 INVITING 팀들을 최신순으로 반환한다. 없으면 빈 리스트.
 */
interface GetReceivedInvitationsUseCase {

	/** [userId]가 받은(INVITED) 대기 중 초대들을 최신순으로 조회한다. */
	fun get(userId: Long): List<ReceivedInvitation>
}
