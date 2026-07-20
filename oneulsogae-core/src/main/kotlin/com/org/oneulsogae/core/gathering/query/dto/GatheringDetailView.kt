package com.org.oneulsogae.core.gathering.query.dto

import com.org.oneulsogae.common.gathering.GatheringType

/**
 * 유저용 모임 상세 한 건(read model). 목록과 달리 소개·인원 + 모임 일정 목록([schedules])까지 포함한다.
 * 참가비는 모임이 아니라 각 일정([GatheringScheduleView])이 가진다.
 * dao는 [imageKey]까지 채우고 [imageUrl]은 null로 둔다. 서비스가 presign 결과로 [imageUrl]을 채운다(이미지 없으면 null).
 * [schedules]는 dao의 별도 조회 결과를 서비스가 채운다(dao 투영 시엔 빈 리스트).
 * 노출 대상은 모집중(RECRUITING) 모임뿐이라 상태(status)는 표시하지 않는다(그 외 상태는 조회 시 404).
 */
data class GatheringDetailView(
	val id: Long,
	val type: GatheringType,
	val title: String,
	val description: String?,
	val imageKey: String?,
	val imageUrl: String? = null,
	val region: String,
	val minParticipants: Int,
	val maxParticipants: Int,
	val schedules: List<GatheringScheduleView> = emptyList(),
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
	) : this(
		id, type, title, description, imageKey, null, region,
		minParticipants, maxParticipants,
	)

	/** [scheduleId] 일정을 찾는다. 이 모임의 일정이 아니면 null. */
	fun scheduleOrNull(scheduleId: Long): GatheringScheduleView? =
		schedules.find { schedule: GatheringScheduleView -> schedule.id == scheduleId }
}
