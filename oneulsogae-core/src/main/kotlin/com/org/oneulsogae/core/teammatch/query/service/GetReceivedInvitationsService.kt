package com.org.oneulsogae.core.teammatch.query.service

import com.org.oneulsogae.core.teammatch.query.dao.GetReceivedInvitationsDao
import com.org.oneulsogae.core.teammatch.query.dto.ReceivedInvitation
import com.org.oneulsogae.core.teammatch.query.service.port.`in`.GetReceivedInvitationsUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetReceivedInvitationsUseCase] 구현. (조회 전용)
 * 요청자가 INVITED 구성원인 INVITING 팀들을 dao로 조회한다. (query dao만 의존, command 포트·도메인 미참조)
 */
@Service
@Transactional(readOnly = true)
class GetReceivedInvitationsService(
	private val getReceivedInvitationsDao: GetReceivedInvitationsDao,
) : GetReceivedInvitationsUseCase {

	override fun get(userId: Long): List<ReceivedInvitation> =
		getReceivedInvitationsDao.findInvited(userId)
}
