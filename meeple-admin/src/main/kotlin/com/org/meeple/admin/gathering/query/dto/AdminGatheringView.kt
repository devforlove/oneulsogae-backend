package com.org.meeple.admin.gathering.query.dto

import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.common.gathering.GatheringType
import java.time.LocalDateTime

/** 어드민 모임 목록 한 건(read model). 소개·참가비 상세는 상세 조회에서만 노출한다. */
data class AdminGatheringView(
	val id: Long,
	val type: GatheringType,
	val title: String,
	val imageUrl: String?,
	val region: String,
	val gatheringAt: LocalDateTime,
	val capacity: Int,
	val status: GatheringStatus,
	val createdAt: LocalDateTime?,
)
