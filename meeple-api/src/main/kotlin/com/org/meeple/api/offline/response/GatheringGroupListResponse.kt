package com.org.meeple.api.offline.response

import com.org.meeple.common.gathering.GatheringType
import com.org.meeple.core.gathering.query.dto.GatheringTypeGroup
import com.org.meeple.core.gathering.query.dto.GatheringView
import com.org.meeple.core.gathering.query.dto.GroupedGatherings
import java.time.LocalDateTime

/**
 * 오프라인 모임 목록 응답. 모임 타입별 그룹([groups])으로 내려준다.
 * 타입 3종을 항상 모두 포함하며, 해당 타입 모임이 없으면 [Group.gatherings]가 빈 배열이다.
 */
data class GatheringGroupListResponse(
	val groups: List<Group>,
) {

	/** 모임 타입 한 종류의 그룹. */
	data class Group(
		val type: GatheringType,
		val typeDescription: String,
		val gatherings: List<Item>,
	)

	/** 모임 한 건(목록 항목). */
	data class Item(
		val id: Long,
		val imageUrl: String?,
		val region: String,
		val title: String,
		val gatheringAt: LocalDateTime,
	)

	companion object {

		fun from(grouped: GroupedGatherings): GatheringGroupListResponse =
			GatheringGroupListResponse(
				groups = grouped.values.map { group: GatheringTypeGroup ->
					Group(
						type = group.type,
						typeDescription = group.typeDescription,
						gatherings = group.gatherings.map { view: GatheringView ->
							Item(
								id = view.id,
								imageUrl = view.imageUrl,
								region = view.region,
								title = view.title,
								gatheringAt = view.gatheringAt,
							)
						},
					)
				},
			)
	}
}
