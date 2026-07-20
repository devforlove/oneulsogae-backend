package com.org.oneulsogae.core.gathering.query.dto

import com.org.oneulsogae.common.gathering.GatheringType

/** 모임 타입 한 종류의 그룹. [gatherings]는 해당 타입 모임 목록(없으면 빈 리스트). */
data class GatheringTypeGroup(
	val type: GatheringType,
	val typeDescription: String,
	val gatherings: List<GatheringView>,
)
