package com.org.oneulsogae.common.match

/** 2:2 매칭에서 한 팀([com.org.oneulsogae.infra.teammatch.command.entity.TeamEntity])의 결성 상태. */
enum class TeamStatus(val description: String) {

	/** 초대중. 구성원을 초대해 팀을 결성하는 중인 상태. */
	INVITING("초대중"),

	/** 팀결성. 구성원이 모두 모여 팀이 완성된 상태. */
	ACTIVE("팀결성"),

	/** 해체중. 구성원 일부가 떠났지만 아직 남은 활성 구성원이 있는 상태. (마지막 구성원까지 떠나면 [DEACTIVATED]) */
	DISBANDED("해체중"),

	/** 비활성화. 팀이 해체되어 매칭에서 빠진 상태. */
	DEACTIVATED("비활성화"),
}
