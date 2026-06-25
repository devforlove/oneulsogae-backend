package com.org.meeple.core.match.query.service

import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.match.query.dao.GetMatchedTeamDao
import com.org.meeple.core.match.query.dao.GetMyTeamDao
import com.org.meeple.core.match.query.dao.GetReceivedInvitationsDao
import com.org.meeple.core.match.query.dao.GetRecommendedTeamDao
import com.org.meeple.core.match.query.dto.MeetingTab
import com.org.meeple.core.match.query.dto.MyTeam
import com.org.meeple.core.match.query.dto.RecommendedTeam
import com.org.meeple.core.match.query.service.port.`in`.GetMeetingTabUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetMeetingTabUseCase] 구현. (조회 전용)
 * 미팅탭 화면에 필요한 세 가지(추천/매칭 팀·받은 초대 개수·내 결성 팀)를 각 query dao로 독립 조회해 조합한다.
 * 팀 카드 슬롯([MeetingTab.recommendedTeams])은 결성 팀 유무로 갈린다 — 팀이 없으면 추천 팀, 결성(ACTIVE) 팀이 있으면 그 팀과 진행 중으로 매칭된 상대 팀.
 * (query dao만 의존, command 포트·도메인 미참조)
 */
@Service
@Transactional(readOnly = true)
class GetMeetingTabService(
	private val getRecommendedTeamDao: GetRecommendedTeamDao,
	private val getMatchedTeamDao: GetMatchedTeamDao,
	private val getReceivedInvitationsDao: GetReceivedInvitationsDao,
	private val getMyTeamDao: GetMyTeamDao,
	private val timeGenerator: TimeGenerator,
) : GetMeetingTabUseCase {

	override fun get(userId: Long): MeetingTab {
		val myTeam: MyTeam? = getMyTeamDao.findMyTeam(userId)
		return MeetingTab(
			// 결성 팀이 없으면 추천 팀, 있으면 그 팀과 진행 중으로 매칭된 상대 팀을 같은 슬롯에 내려준다.
			recommendedTeams = teamCardsFor(userId, myTeam),
			receivedInvitationCount = getReceivedInvitationsDao.countInvited(userId),
			myTeam = myTeam,
		)
	}

	private fun teamCardsFor(userId: Long, myTeam: MyTeam?): List<RecommendedTeam> =
		if (myTeam == null) {
			getRecommendedTeamDao.findByUserId(userId)
		} else {
			getMatchedTeamDao.findInProgressByTeamId(myTeam.teamId, timeGenerator.now())
		}
}
