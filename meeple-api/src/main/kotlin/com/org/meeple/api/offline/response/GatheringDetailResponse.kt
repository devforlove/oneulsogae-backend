package com.org.meeple.api.offline.response

import com.org.meeple.common.gathering.GatheringScheduleStatus
import com.org.meeple.common.gathering.GatheringType
import com.org.meeple.common.user.Gender
import com.org.meeple.core.gathering.query.dto.GatheringDetailView
import com.org.meeple.core.gathering.query.dto.GatheringScheduleView
import java.time.LocalDateTime

/**
 * 오프라인 모임 상세 응답. 소개·인원 + 모임 일정 목록([schedules])을 flat하게 내려준다.
 * imageKey는 응답에서 제외하고 presigned [imageUrl]만 노출한다. (모집중 모임만 조회되므로 상태는 포함하지 않는다)
 * [viewerGender]는 로그인한 상태로 조회했을 때만 채워지는 조회자 성별이다. (비로그인이거나 성별 미설정이면 null)
 *
 * [schedules]는 유저에게 셀렉트박스로 보여줄 목록이라, 한 일정을 성별([Schedule.gender])로 나눈 아이템으로 내려준다.
 * 유저는 한 성별이므로 각 아이템은 해당 성별의 참가비(정상가·얼리버드·할인가)만 담는다.
 * 로그인 상태(성별 확정)면 조회자 성별 아이템만, 비로그인이면 남/녀 두 아이템을 모두 포함한다. (일정 시작 시각 오름차순, 같은 일정은 남성→여성 순)
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
	// 성별로 나뉜 일정 아이템 목록(시작 시각 오름차순). 일정이 없으면 빈 배열.
	val schedules: List<Schedule>,
	// 로그인 조회자의 성별. 비로그인/성별 미설정이면 null.
	val viewerGender: Gender?,
) {

	/**
	 * 성별로 나뉜 일정 한 건(셀렉트박스 아이템). 같은 일정([scheduleId])이라도 남/녀는 별개 아이템이다.
	 * 참가비는 이 아이템의 성별([gender]) 값만 담는다: 정상가([fee], 필수)·얼리버드([earlyBirdFee], 선택)·할인가([discountFee], 선택).
	 * 얼리버드 정원·남은 개수([earlyBirdCapacity]·[earlyBirdRemaining])는 일정 공통이라 성별과 무관하게 같은 값이다.
	 */
	data class Schedule(
		val scheduleId: Long,
		val gender: Gender,
		val genderDescription: String,
		val startAt: LocalDateTime,
		val endAt: LocalDateTime?,
		val fee: Int,
		val earlyBirdFee: Int?,
		val earlyBirdCapacity: Int?,
		val earlyBirdRemaining: Int?,
		val discountFee: Int?,
		val status: GatheringScheduleStatus,
		val statusDescription: String,
	) {
		companion object {
			/** [view] 일정을 [gender] 성별 아이템으로 만든다. (해당 성별의 참가비만 담는다) */
			fun of(view: GatheringScheduleView, gender: Gender): Schedule =
				Schedule(
					scheduleId = view.id,
					gender = gender,
					genderDescription = gender.description,
					startAt = view.startAt,
					endAt = view.endAt,
					fee = if (gender == Gender.MALE) view.maleFee else view.femaleFee,
					earlyBirdFee = if (gender == Gender.MALE) view.earlyBirdMaleFee else view.earlyBirdFemaleFee,
					earlyBirdCapacity = view.earlyBirdCapacity,
					earlyBirdRemaining = view.earlyBirdRemaining,
					discountFee = if (gender == Gender.MALE) view.discountMaleFee else view.discountFemaleFee,
					status = view.status,
					statusDescription = view.status.description,
				)
		}
	}

	companion object {

		/**
		 * [viewerGender]는 로그인 조회 시에만 채운다(비로그인이면 null).
		 * 일정은 [viewerGender]가 있으면 그 성별 아이템만, 없으면 남/녀 두 아이템으로 펼친다.
		 */
		fun of(view: GatheringDetailView, viewerGender: Gender?): GatheringDetailResponse {
			val genders: List<Gender> = viewerGender?.let { listOf(it) } ?: listOf(Gender.MALE, Gender.FEMALE)
			return GatheringDetailResponse(
				id = view.id,
				type = view.type,
				typeDescription = view.type.description,
				title = view.title,
				description = view.description,
				imageUrl = view.imageUrl,
				region = view.region,
				minParticipants = view.minParticipants,
				maxParticipants = view.maxParticipants,
				schedules = view.schedules.flatMap { schedule: GatheringScheduleView ->
					genders.map { gender: Gender -> Schedule.of(schedule, gender) }
				},
				viewerGender = viewerGender,
			)
		}
	}
}
