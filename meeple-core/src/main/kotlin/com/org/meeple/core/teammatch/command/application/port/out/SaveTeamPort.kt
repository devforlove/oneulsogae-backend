package com.org.meeple.core.teammatch.command.application.port.out

import com.org.meeple.core.teammatch.command.domain.Team

/**
 * 팀(헤더+구성원) 저장 아웃포트.
 * 구현은 infra의 어댑터가 [com.org.meeple.infra.teammatch.command.entity.TeamEntity]/[com.org.meeple.infra.teammatch.command.entity.TeamMemberEntity]로 영속화한다.
 */
interface SaveTeamPort {

	/** 팀 애그리거트(헤더+구성원)를 저장하고, 식별자가 채워진 팀을 반환한다. */
	fun save(team: Team): Team
}
