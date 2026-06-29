package com.org.meeple.common.match

/** 2:2 매칭에서 한 팀([com.org.meeple.infra.teammatch.command.entity.TeamEntity]) 구성원의 활성 상태. */
enum class TeamMemberStatus(val description: String) {

	/** 초대중. 팀에 초대되었으나 아직 합류를 수락하지 않은 상태. */
	INVITED("초대중"),

	/** 활성 상태. 팀에 정상 합류 중인 상태. */
	ACTIVE("활성"),

	/** 비활성 상태. 팀 구성원에서 빠진 상태. */
	DEACTIVE("비활성"),
}
