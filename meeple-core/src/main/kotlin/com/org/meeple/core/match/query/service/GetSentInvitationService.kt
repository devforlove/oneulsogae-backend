package com.org.meeple.core.match.query.service

import com.org.meeple.core.match.query.dao.GetSentInvitationDao
import com.org.meeple.core.match.query.dto.SentInvitation
import com.org.meeple.core.match.query.service.port.`in`.GetSentInvitationUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetSentInvitationUseCase] 구현. (조회 전용)
 * 요청자가 ACTIVE 구성원(=초대자)인 가장 최근 INVITING 팀을 dao로 조회한다. 없으면 null.
 * (query dao만 의존하고 command 포트·도메인을 참조하지 않는다)
 */
@Service
@Transactional(readOnly = true)
class GetSentInvitationService(
	private val getSentInvitationDao: GetSentInvitationDao,
) : GetSentInvitationUseCase {

	override fun get(userId: Long): SentInvitation? =
		getSentInvitationDao.findLatestInviting(userId)
}
