package com.org.meeple.core.match.query.dao

import com.org.meeple.core.match.query.dto.SentInvitation

/**
 * 내가 보낸 초대 현황 조회 dao(query out-port). 실제 QueryDSL 구현은 infra가 담당한다.
 * 요청자가 ACTIVE 구성원(=초대자)인 INVITING·ACTIVE 팀 중 가장 최근 1건을 상대 구성원과 함께 조회한다.
 */
interface GetSentInvitationDao {

	/** [userId]가 ACTIVE 구성원인 가장 최근 INVITING·ACTIVE 팀을 상대 구성원과 함께 조회한다. 없으면 null. */
	fun findLatestInviting(userId: Long): SentInvitation?
}
