package com.org.oneulsogae.common.match

/**
 * 팀 매칭([com.org.oneulsogae.infra.teammatch.command.entity.TeamMatchEntity])에 참가한 한 팀([com.org.oneulsogae.infra.teammatch.command.entity.MatchedTeamEntity])의 상태.
 * 같은 팀이라도 매치마다 상태가 다르므로 팀이 아니라 참가 행이 보관한다.
 * WAITING(소개 직후) → APPLY(이 팀이 매칭 신청) → ACTIVE(양 팀 성사) / DEACTIVE(팀 해체).
 */
enum class MatchedTeamStatus(val description: String) {

	/** 대기. 소개(승격) 직후, 매칭 신청 전 상태. */
	WAITING("대기"),

	/** 신청. 이 팀이 매칭을 신청했으나 아직 성사되지 않은 상태. */
	APPLY("신청"),

	/** 활성. 양 팀이 신청해 팀 매칭이 성사된 활성 상태. */
	ACTIVE("활성"),

	/** 비활성. 팀의 모든 구성원이 탈퇴(팀 해체)해 매칭 참가가 비활성화된 상태. */
	DEACTIVE("비활성"),
}
