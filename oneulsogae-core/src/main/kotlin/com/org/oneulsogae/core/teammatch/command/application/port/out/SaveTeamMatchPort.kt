package com.org.oneulsogae.core.teammatch.command.application.port.out

import com.org.oneulsogae.core.teammatch.command.domain.TeamMatch

/** 팀 매칭 애그리거트(헤더 + 참가 팀) 저장 포트. */
interface SaveTeamMatchPort {

	fun save(teamMatch: TeamMatch): TeamMatch
}
