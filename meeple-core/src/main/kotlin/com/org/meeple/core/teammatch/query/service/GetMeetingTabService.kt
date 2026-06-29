package com.org.meeple.core.teammatch.query.service

import com.org.meeple.common.match.TeamStatus
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.teammatch.query.dao.GetMatchedTeamDao
import com.org.meeple.core.teammatch.query.dao.GetMyTeamDao
import com.org.meeple.core.teammatch.query.dao.GetReceivedInvitationsDao
import com.org.meeple.core.teammatch.query.dao.GetRecommendedTeamDao
import com.org.meeple.core.teammatch.query.dto.MeetingTab
import com.org.meeple.core.teammatch.query.dto.MyTeam
import com.org.meeple.core.teammatch.query.dto.RecommendedTeam
import com.org.meeple.core.teammatch.query.service.port.`in`.GetMeetingTabUseCase
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * [GetMeetingTabUseCase] 구현.
 * 미팅탭 화면에 필요한 세 가지(추천/매칭 팀·받은 초대 개수·내 팀)를 각 query dao로 독립 조회해 조합한다.
 * 팀 카드 슬롯([MeetingTab.recommendedTeams])은 내 팀 상태로 갈린다 — 결성(ACTIVE) 팀이 있으면 그 팀과 진행 중으로 매칭된 상대 팀, 팀이 없거나 초대중(INVITING)이면 추천 팀.
 * 부수효과 없는 순수 조회다 — 추천 팀(recommended_teams)의 첫 적재는 회사 이메일 인증 완료 시점([com.org.meeple.core.user.command.application.UserEventHandler])이 담당한다.
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
			// 결성(ACTIVE) 팀이 있으면 그 팀과 매칭된 상대 팀, 팀이 없거나 초대중(INVITING)이면 추천 팀을 같은 슬롯에 내려준다.
			recommendedTeams = teamCardsFor(userId, myTeam),
			receivedInvitationCount = getReceivedInvitationsDao.countInvited(userId),
			myTeam = myTeam,
		)
	}

	// 결성(ACTIVE)/해체중(DISBANDED) 팀이 있을 때만 그 팀과 진행 중으로 매칭된 상대 팀을 보여준다. (DISBANDED는 한 명이 나갔어도 매칭이 유지되므로 매칭 경로) 팀이 없거나 초대중(INVITING)이면 매칭 전이라 추천 팀을 보여준다.
	private fun teamCardsFor(userId: Long, myTeam: MyTeam?): List<RecommendedTeam> =
		if (myTeam?.status == TeamStatus.ACTIVE || myTeam?.status == TeamStatus.DISBANDED) {
			getMatchedTeamDao.findInProgressByTeamId(myTeam.teamId, timeGenerator.now())
		} else {
			// 추천은 회사 인증 완료 시점에 미리 적재되므로, 여기서는 적재된 추천을 읽기만 한다. (없으면 빈 리스트)
			getRecommendedTeamDao.findByUserId(userId)
		}
}
