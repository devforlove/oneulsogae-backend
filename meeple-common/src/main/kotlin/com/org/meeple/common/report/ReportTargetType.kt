package com.org.meeple.common.report

/** 신고 대상 종류. 신고 대상 id가 상대 유저([USER])인지 상대 팀([TEAM])인지 구분한다. */
enum class ReportTargetType {
	/** 1:1(소개) 신고. 대상 id는 상대 유저(users.id). */
	USER,

	/** 팀(미팅) 신고. 대상 id는 상대 팀(teams.id). */
	TEAM,
}
