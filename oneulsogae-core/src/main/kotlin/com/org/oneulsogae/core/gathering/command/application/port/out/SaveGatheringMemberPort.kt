package com.org.oneulsogae.core.gathering.command.application.port.out

import com.org.oneulsogae.core.gathering.command.domain.GatheringMember

/** 참가 행을 저장(신규 insert 또는 기존 행 갱신)하는 아웃포트. */
interface SaveGatheringMemberPort {

	fun save(member: GatheringMember): GatheringMember
}
