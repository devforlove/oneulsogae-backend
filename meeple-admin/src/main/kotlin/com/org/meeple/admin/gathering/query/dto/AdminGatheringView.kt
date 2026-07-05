package com.org.meeple.admin.gathering.query.dto

import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.common.gathering.GatheringType
import java.time.LocalDateTime

/**
 * 어드민 모임 목록 한 건(read model). 소개·참가비 상세는 상세 조회에서만 노출한다.
 * dao는 [imageKey]까지 채우고 [imageUrl]은 null로 둔다. 서비스가 presign 결과로 [imageUrl]을 채운다(이미지 없으면 null).
 */
data class AdminGatheringView(
	val id: Long,
	val type: GatheringType,
	val title: String,
	val imageKey: String?,
	val imageUrl: String? = null,
	val region: String,
	val gatheringAt: LocalDateTime,
	val minParticipants: Int,
	val maxParticipants: Int,
	val status: GatheringStatus,
	val createdAt: LocalDateTime?,
) {
	/** dao 투영용 생성자. imageUrl은 서비스가 presign으로 채운다. */
	constructor(
		id: Long,
		type: GatheringType,
		title: String,
		imageKey: String?,
		region: String,
		gatheringAt: LocalDateTime,
		minParticipants: Int,
		maxParticipants: Int,
		status: GatheringStatus,
		createdAt: LocalDateTime?,
	) : this(id, type, title, imageKey, null, region, gatheringAt, minParticipants, maxParticipants, status, createdAt)
}
