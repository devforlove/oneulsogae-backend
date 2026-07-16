package com.org.meeple.api.admin.response

import com.org.meeple.admin.gathering.query.dto.AdminGatheringDetailView
import com.org.meeple.admin.gathering.query.dto.AdminGatheringScheduleView
import com.org.meeple.common.gathering.GatheringScheduleStatus
import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.common.gathering.GatheringType
import java.time.LocalDateTime

/** 어드민 모임 상세 응답. 목록 필드 + 소개 + 모임 일정 목록([schedules]). 참가비는 각 일정([Schedule])이 가진다. */
data class AdminGatheringDetailResponse(
	val id: Long,
	val type: GatheringType,
	val title: String,
	val description: String?,
	val imageUrl: String?,
	val region: String,
	val minParticipants: Int,
	val maxParticipants: Int,
	val status: GatheringStatus,
	val createdAt: LocalDateTime?,
	// 모임 일정 목록(시작 시각 오름차순). 일정이 없으면 빈 배열.
	val schedules: List<Schedule>,
) {

	/** 모임 일정 한 건. 참가비(성별·티어별, 없는 티어는 null)를 포함한다. */
	data class Schedule(
		val id: Long,
		val startAt: LocalDateTime,
		val endAt: LocalDateTime?,
		val maleFee: Int,
		val femaleFee: Int,
		// 얼리버드가(남/녀, 저장 금액). 얼리버드가 없는 일정은 null. (할인율이 아니라 금액으로 노출한다)
		val earlyBirdMaleFee: Int?,
		val earlyBirdFemaleFee: Int?,
		val earlyBirdCapacity: Int?,
		// 얼리버드 특가 남은 개수. 특가가 없는 일정은 null.
		val earlyBirdRemaining: Int?,
		val discountMaleFee: Int?,
		val discountFemaleFee: Int?,
		val status: GatheringScheduleStatus,
		val statusDescription: String,
	) {
		companion object {
			fun of(view: AdminGatheringScheduleView): Schedule =
				Schedule(
					id = view.id,
					startAt = view.startAt,
					endAt = view.endAt,
					maleFee = view.maleFee,
					femaleFee = view.femaleFee,
					earlyBirdMaleFee = view.earlyBirdMaleFee,
					earlyBirdFemaleFee = view.earlyBirdFemaleFee,
					earlyBirdCapacity = view.earlyBirdCapacity,
					earlyBirdRemaining = view.earlyBirdRemaining,
					discountMaleFee = view.discountMaleFee,
					discountFemaleFee = view.discountFemaleFee,
					status = view.status,
					statusDescription = view.status.description,
				)
		}
	}

	companion object {
		fun of(view: AdminGatheringDetailView): AdminGatheringDetailResponse =
			AdminGatheringDetailResponse(
				id = view.id,
				type = view.type,
				title = view.title,
				description = view.description,
				imageUrl = view.imageUrl,
				region = view.region,
				minParticipants = view.minParticipants,
				maxParticipants = view.maxParticipants,
				status = view.status,
				createdAt = view.createdAt,
				schedules = view.schedules.map { schedule: AdminGatheringScheduleView -> Schedule.of(schedule) },
			)
	}
}
