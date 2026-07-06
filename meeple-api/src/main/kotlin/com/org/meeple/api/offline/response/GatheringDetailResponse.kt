package com.org.meeple.api.offline.response

import com.org.meeple.common.gathering.GatheringScheduleStatus
import com.org.meeple.common.gathering.GatheringType
import com.org.meeple.core.gathering.query.dto.GatheringDetailView
import com.org.meeple.core.gathering.query.dto.GatheringScheduleView
import java.time.LocalDateTime

/**
 * 오프라인 모임 상세 응답. 소개·인원 + 모임 일정 목록([schedules])을 flat하게 내려준다. 참가비는 각 일정([Schedule])이 가진다.
 * imageKey는 응답에서 제외하고 presigned [imageUrl]만 노출한다. (모집중 모임만 조회되므로 상태는 포함하지 않는다)
 */
data class GatheringDetailResponse(
	val id: Long,
	val type: GatheringType,
	val typeDescription: String,
	val title: String,
	val description: String?,
	val imageUrl: String?,
	val region: String,
	val minParticipants: Int,
	val maxParticipants: Int,
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
		val earlyBirdMaleFee: Int?,
		val earlyBirdFemaleFee: Int?,
		val earlyBirdCapacity: Int?,
		val discountMaleFee: Int?,
		val discountFemaleFee: Int?,
		val status: GatheringScheduleStatus,
		val statusDescription: String,
	) {
		companion object {
			fun of(view: GatheringScheduleView): Schedule =
				Schedule(
					id = view.id,
					startAt = view.startAt,
					endAt = view.endAt,
					maleFee = view.maleFee,
					femaleFee = view.femaleFee,
					earlyBirdMaleFee = view.earlyBirdMaleFee,
					earlyBirdFemaleFee = view.earlyBirdFemaleFee,
					earlyBirdCapacity = view.earlyBirdCapacity,
					discountMaleFee = view.discountMaleFee,
					discountFemaleFee = view.discountFemaleFee,
					status = view.status,
					statusDescription = view.status.description,
				)
		}
	}

	companion object {

		fun of(view: GatheringDetailView): GatheringDetailResponse =
			GatheringDetailResponse(
				id = view.id,
				type = view.type,
				typeDescription = view.type.description,
				title = view.title,
				description = view.description,
				imageUrl = view.imageUrl,
				region = view.region,
				minParticipants = view.minParticipants,
				maxParticipants = view.maxParticipants,
				schedules = view.schedules.map { schedule: GatheringScheduleView -> Schedule.of(schedule) },
			)
	}
}
