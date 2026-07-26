package com.org.oneulsogae.core.mission.query.dto

import com.org.oneulsogae.common.mission.MissionType

/**
 * 미션 목록 한 건(read model). 정의에 사용자별 상태(완료 여부·수령 가능 여부)를 얹는다.
 * [completed]면 이미 보상을 받은 미션, 아니면 [eligible]로 지금 받을 수 있는지 표시한다.
 */
data class MissionView(
	val missionId: Long,
	val type: MissionType,
	val title: String,
	val description: String?,
	val rewardCoin: Int,
	val completed: Boolean,
	val eligible: Boolean,
)
