package com.org.meeple.admin.gathering.query.dto

import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.common.gathering.GatheringType
import java.time.LocalDateTime

/**
 * 어드민 모임 상세 read model. 목록 필드 + 소개 + 모임 일정 목록([schedules]).
 * 참가비는 모임이 아니라 각 일정([AdminGatheringScheduleView])이 가진다.
 * dao는 [imageKey]까지 채우고 [imageUrl]은 null로 둔다. 서비스가 presign 결과로 [imageUrl]을 채운다(이미지 없으면 null).
 * [schedules]는 dao의 별도 조회 결과를 서비스가 채운다(dao 투영 시엔 빈 리스트).
 * (조회 전용이라 command 도메인 값 객체를 참조하지 않고 자체 flat read model로 둔다)
 */
data class AdminGatheringDetailView(
	val id: Long,
	val type: GatheringType,
	val title: String,
	val description: String?,
	val imageKey: String?,
	val imageUrl: String? = null,
	val region: String,
	val minParticipants: Int,
	val maxParticipants: Int,
	val status: GatheringStatus,
	val createdAt: LocalDateTime?,
	val schedules: List<AdminGatheringScheduleView> = emptyList(),
) {
	/** dao 투영용 생성자. imageUrl은 서비스가 presign으로, schedules는 서비스가 별도 조회로 채운다. */
	constructor(
		id: Long,
		type: GatheringType,
		title: String,
		description: String?,
		imageKey: String?,
		region: String,
		minParticipants: Int,
		maxParticipants: Int,
		status: GatheringStatus,
		createdAt: LocalDateTime?,
	) : this(
		id, type, title, description, imageKey, null, region, minParticipants, maxParticipants, status, createdAt,
	)
}
