package com.org.oneulsogae.api.mission.response

import com.org.oneulsogae.common.mission.MissionType
import com.org.oneulsogae.core.mission.query.dto.MissionView
import com.org.oneulsogae.core.mission.query.dto.MissionViews

/**
 * 미션 목록 한 건 응답. [completed]면 이미 보상을 받은 미션이고, 미완료면 [eligible]로 지금 수령 가능한지 표시한다.
 */
data class MissionResponse(
	val missionId: Long,
	val type: MissionType,
	val title: String,
	val description: String?,
	val rewardCoin: Int,
	val completed: Boolean,
	val eligible: Boolean,
) {
	companion object {

		fun of(view: MissionView): MissionResponse =
			MissionResponse(
				missionId = view.missionId,
				type = view.type,
				title = view.title,
				description = view.description,
				rewardCoin = view.rewardCoin,
				completed = view.completed,
				eligible = view.eligible,
			)

		fun listOf(views: MissionViews): List<MissionResponse> =
			views.values.map { view: MissionView -> of(view) }
	}
}
