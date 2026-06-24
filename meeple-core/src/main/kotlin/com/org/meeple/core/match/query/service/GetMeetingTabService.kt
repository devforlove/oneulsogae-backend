package com.org.meeple.core.match.query.service

import com.org.meeple.core.match.query.dao.GetMyActiveTeamDao
import com.org.meeple.core.match.query.dao.GetReceivedInvitationsDao
import com.org.meeple.core.match.query.dao.GetRecommendedTeamDao
import com.org.meeple.core.match.query.dto.MeetingTab
import com.org.meeple.core.match.query.service.port.`in`.GetMeetingTabUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetMeetingTabUseCase] 구현. (조회 전용)
 * 미팅탭 화면에 필요한 세 가지(추천 팀·받은 초대 개수·내 결성 팀)를 각 query dao로 독립 조회해 조합한다.
 * (query dao만 의존, command 포트·도메인 미참조)
 */
@Service
@Transactional(readOnly = true)
class GetMeetingTabService(
	private val getRecommendedTeamDao: GetRecommendedTeamDao,
	private val getReceivedInvitationsDao: GetReceivedInvitationsDao,
	private val getMyActiveTeamDao: GetMyActiveTeamDao,
) : GetMeetingTabUseCase {

	override fun get(userId: Long): MeetingTab =
		MeetingTab(
			recommendedTeams = getRecommendedTeamDao.findByUserId(userId),
			receivedInvitationCount = getReceivedInvitationsDao.countInvited(userId),
			myActiveTeam = getMyActiveTeamDao.findLatestActiveTeam(userId),
		)
}
