package com.org.oneulsogae.api.admin.response

import com.org.oneulsogae.admin.gathering.query.dto.AdminGatheringView
import com.org.oneulsogae.admin.gathering.query.dto.AdminGatheringViews
import com.org.oneulsogae.common.gathering.GatheringStatus
import com.org.oneulsogae.common.gathering.GatheringType
import java.time.LocalDateTime

/** 어드민 모임 목록 항목 응답. 소개·참가비 상세는 상세 조회에서만 노출한다. */
data class AdminGatheringResponse(
	val id: Long,
	val type: GatheringType,
	val title: String,
	val imageUrl: String?,
	val region: String,
	val minParticipants: Int,
	val maxParticipants: Int,
	val status: GatheringStatus,
	val createdAt: LocalDateTime?,
) {
	companion object {
		private fun of(view: AdminGatheringView): AdminGatheringResponse =
			AdminGatheringResponse(
				id = view.id,
				type = view.type,
				title = view.title,
				imageUrl = view.imageUrl,
				region = view.region,
				minParticipants = view.minParticipants,
				maxParticipants = view.maxParticipants,
				status = view.status,
				createdAt = view.createdAt,
			)

		fun listOf(views: AdminGatheringViews): List<AdminGatheringResponse> =
			views.values.map { view: AdminGatheringView -> of(view) }
	}
}
