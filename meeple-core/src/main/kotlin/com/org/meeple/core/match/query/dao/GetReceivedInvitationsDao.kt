package com.org.meeple.core.match.query.dao

import com.org.meeple.core.match.query.dto.ReceivedInvitation

/**
 * 내가 받은 초대 리스트 조회 dao(query out-port). 실제 QueryDSL 구현은 infra가 담당한다.
 * 요청자가 INVITED 구성원인 INVITING 팀들을 초대자(ACTIVE 구성원) 프로필과 함께 team id desc로 반환한다.
 */
interface GetReceivedInvitationsDao {

	/** [userId]가 INVITED 구성원인 INVITING 팀들을 초대자 프로필과 함께 최신순으로 반환한다. */
	fun findInvited(userId: Long): List<ReceivedInvitation>
}
