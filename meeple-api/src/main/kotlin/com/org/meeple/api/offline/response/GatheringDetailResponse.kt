package com.org.meeple.api.offline.response

import com.org.meeple.common.gathering.GatheringMemberStatus
import com.org.meeple.common.gathering.GatheringType
import com.org.meeple.common.user.Gender
import com.org.meeple.core.common.time.ageAt
import com.org.meeple.core.gathering.query.dto.GatheringDetailView
import com.org.meeple.core.gathering.query.dto.GatheringParticipantView
import com.org.meeple.core.gathering.query.dto.GatheringScheduleView
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 오프라인 모임 상세 응답. 소개·인원 + 모임 일정 목록([schedules])을 flat하게 내려준다.
 * imageKey는 응답에서 제외하고 presigned [imageUrl]만 노출한다. (모집중 모임만 조회되므로 상태는 포함하지 않는다)
 * [viewerGender]는 로그인한 상태로 조회했을 때만 채워지는 조회자 성별이다. (비로그인이거나 성별 미설정이면 null)
 *
 * [schedules]는 유저에게 셀렉트박스로 보여줄 목록이라, 한 일정을 성별([Schedule.gender])로 나눈 아이템으로 내려준다.
 * 유저는 한 성별이므로 각 아이템은 해당 성별의 참가비(정상가·할인가)만 담는다.
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
	 * 참가비는 이 아이템의 성별([gender]) 값만 담되, 얼리버드 남은 개수([GatheringScheduleView.earlyBirdRemaining])에 따라 노출 티어가 달라진다.
	 * 얼리버드가와 일반 할인가는 동시에 노출되지 않으므로 [discountFee] 하나로 합치고, 그 값이 얼리버드가인지를 [isEarlyBird]로 구분한다:
	 * - 얼리버드 티어가 없으면(remaining null): 정상가([fee])만, [discountFee]는 null, [isEarlyBird]=false.
	 * - 얼리버드가 남아있으면(remaining > 0): 정상가([fee])·얼리버드가([discountFee]), [isEarlyBird]=true.
	 * - 얼리버드가 모두 소진되면(remaining <= 0): 정상가([fee])·일반 할인가([discountFee]), [isEarlyBird]=false.
	 * [status]는 일정 상태이되, 해당 성별 정원이 모두 소진되면 소진됨(SOLD_OUT)으로 내려간다.
	 * [productId]는 이 성별의 적용 티어 상품 id(얼리버드 유효 → EARLY_BIRD, 소진 & 할인가 존재 → DISCOUNT, 그 외 NORMAL)로,
	 * 노출 중인 실결제가와 같은 행을 가리키며 체크아웃·결제완료 요청에 그대로 쓴다.
	 */
	data class Schedule(
		val scheduleId: Long,
		val gender: Gender,
		val genderDescription: String,
		val productId: Long,
		val startAt: LocalDateTime,
		val endAt: LocalDateTime?,
		val fee: Int?,
		val discountFee: Int?,
		val isEarlyBird: Boolean,
		val status: GatheringScheduleItemStatus,
		val statusDescription: String,
		// 이 일정의 참가자 로스터(승인대기·참가)를 성별로 그룹핑. 성별과 무관한 전체 로스터라, 비로그인의 남/녀 두 아이템에 동일하게 담긴다.
		val participants: ScheduleParticipants,
	) {
		companion object {
			/** [view] 일정을 [gender] 성별 아이템으로 만든다. (해당 성별의 참가비·정원 소진 여부를 반영한다. 금액 티어 계산은 [GatheringScheduleView]에 캡슐화되어 있다) */
			fun of(view: GatheringScheduleView, gender: Gender, today: LocalDate): Schedule {
				val status: GatheringScheduleItemStatus = GatheringScheduleItemStatus.of(view.status, view.soldOutFor(gender))
				val earlyBirdFee: Int? = view.earlyBirdFeeFor(gender)
				return Schedule(
					scheduleId = view.id,
					gender = gender,
					genderDescription = gender.description,
					productId = view.productIdFor(gender),
					startAt = view.startAt,
					endAt = view.endAt,
					fee = view.feeFor(gender),
					discountFee = earlyBirdFee ?: if (view.earlyBirdSoldOut) view.discountFeeFor(gender) else null,
					isEarlyBird = earlyBirdFee != null,
					status = status,
					statusDescription = status.description,
					participants = ScheduleParticipants.of(view.participants, today),
				)
			}
		}
	}

	/** 일정 참가자 로스터를 성별로 그룹핑한다. 해당 성별 참가자가 없으면 빈 배열. */
	data class ScheduleParticipants(
		val male: List<Participant>,
		val female: List<Participant>,
	) {
		companion object {
			fun of(views: List<GatheringParticipantView>, today: LocalDate): ScheduleParticipants {
				val byGender: Map<Gender, List<GatheringParticipantView>> =
					views.groupBy { view: GatheringParticipantView -> view.gender }
				return ScheduleParticipants(
					male = byGender[Gender.MALE].orEmpty().map { view: GatheringParticipantView -> Participant.of(view, today) },
					female = byGender[Gender.FEMALE].orEmpty().map { view: GatheringParticipantView -> Participant.of(view, today) },
				)
			}
		}
	}

	/**
	 * 일정 참가자 한 명. 성별은 상위 그룹([ScheduleParticipants.male]/[ScheduleParticipants.female])이 나타내므로 항목엔 담지 않는다.
	 * 참가(JOINED)는 프로필(닉네임·프로필이미지·나이)을 포함하고, 승인대기(PENDING)는 유저 상세를 비운 익명 자리(상태만)로 내려간다.
	 */
	data class Participant(
		val userId: Long?,
		val status: GatheringMemberStatus,
		val statusDescription: String,
		val jobCategory: String?,
		val jobDetail: String?,
		val age: Int?,
		val height: Int?,
		val profileImageCode: String?,
	) {
		companion object {
			/** JOINED만 프로필(gathering_profile 유래 직종·직장상세·나이·키·프로필이미지)을 채우고, PENDING은 상태만 남긴다. (나이는 [today] 기준으로 생일에서 파생) */
			fun of(view: GatheringParticipantView, today: LocalDate): Participant =
				when (view.status) {
					GatheringMemberStatus.JOINED -> Participant(
						userId = view.userId,
						status = view.status,
						statusDescription = view.status.description,
						jobCategory = view.jobCategory,
						jobDetail = view.jobDetail,
						age = view.birthday?.ageAt(today),
						height = view.height,
						profileImageCode = view.profileImageCode,
					)

					else -> Participant(
						userId = null,
						status = view.status,
						statusDescription = view.status.description,
						jobCategory = null,
						jobDetail = null,
						age = null,
						height = null,
						profileImageCode = null,
					)
				}
		}
	}

	companion object {

		/**
		 * [viewerGender]는 로그인 조회 시에만 채운다(비로그인이면 null).
		 * 일정은 [viewerGender]가 있으면 그 성별 아이템만, 없으면 남/녀 두 아이템으로 펼친다.
		 * [today]는 참가자 나이(생일 파생) 계산 기준일이다.
		 */
		fun of(view: GatheringDetailView, viewerGender: Gender?, today: LocalDate): GatheringDetailResponse {
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
					genders.map { gender: Gender -> Schedule.of(schedule, gender, today) }
				},
				viewerGender = viewerGender,
			)
		}
	}
}
