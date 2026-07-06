package com.org.meeple.admin.gathering.command.domain

import com.org.meeple.admin.common.error.AdminErrorCode
import com.org.meeple.admin.common.error.AdminException
import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.common.gathering.GatheringType

private const val TITLE_MAX_LENGTH: Int = 100
private const val DESCRIPTION_MAX_LENGTH: Int = 1000
private const val MIN_PARTICIPANTS: Int = 2

/**
 * 어드민(운영)이 등록하는 모임 도메인 모델(명령 측).
 * (admin은 core에 의존하지 않으므로 core Gathering을 쓰지 않고 자체 모델을 둔다)
 * 운영이 만든 모임이므로 생성자(userId)는 두지 않는다. 영속성에서 user_id는 null(운영 생성)로 저장된다.
 * 인원은 [minParticipants](최소 성사 인원)·[maxParticipants](정원)로 표현한다.
 * 참가비는 성별로 나뉜 값 객체([GatheringFee])로 표현한다: 정상가([fee], 필수),
 * 얼리버드 특가([earlyBirdFee], 선택)·할인가([discountFee], 선택)는 해당 특가가 있는 모임만 값을 가진다.
 * 얼리버드 특가가 있으면 적용 인원 수([earlyBirdCapacity])도 함께 가진다. (특가 가격과 인원은 세트)
 * 생성 시 status는 DRAFT(준비중)이고, 활성화해야 RECRUITING(모집중)이 된다. 영속성은 [com.org.meeple.infra.gathering.command.entity.GatheringEntity]가 담당한다.
 */
data class AdminGathering(
	val id: Long = 0,
	val type: GatheringType,
	val title: String,
	val description: String?,
	val imageKey: String?,
	val region: String,
	val minParticipants: Int,
	val maxParticipants: Int,
	val fee: GatheringFee,
	val earlyBirdFee: GatheringFee?,
	val earlyBirdCapacity: Int?,
	val discountFee: GatheringFee?,
	// 등록 직후는 준비중(DRAFT). 활성화해야 모집중(RECRUITING)이 된다.
	val status: GatheringStatus = GatheringStatus.DRAFT,
) {

	/**
	 * 모임 전체 데이터를 교체한다. (id·status·생성 시각은 보존) [imageKey]는 서비스가 미리 확정한 값이다
	 * (새 이미지가 오면 새 키, 없으면 기존 키 유지). 생성과 동일한 규칙으로 입력을 검증한다.
	 */
	fun update(
		type: GatheringType,
		title: String,
		description: String?,
		imageKey: String?,
		region: String,
		minParticipants: Int,
		maxParticipants: Int,
		fee: GatheringFee,
		earlyBirdFee: GatheringFee?,
		earlyBirdCapacity: Int?,
		discountFee: GatheringFee?,
	): AdminGathering {
		validateGathering(title, description, region, minParticipants, maxParticipants)
		validateEarlyBirdCapacity(earlyBirdFee, earlyBirdCapacity, maxParticipants)
		return copy(
			type = type,
			title = title,
			description = description,
			imageKey = imageKey,
			region = region,
			minParticipants = minParticipants,
			maxParticipants = maxParticipants,
			fee = fee,
			earlyBirdFee = earlyBirdFee,
			earlyBirdCapacity = earlyBirdCapacity,
			discountFee = discountFee,
		)
	}

	companion object {

		/**
		 * [type]·[title]·[description]·[imageKey]·[region]·인원([minParticipants]·[maxParticipants])·
		 * 참가비([fee]·[earlyBirdFee]·[earlyBirdCapacity]·[discountFee])로 모임을 만든다.
		 * 입력을 검증한 뒤 준비중(DRAFT)으로 만든다.
		 */
		fun create(
			type: GatheringType,
			title: String,
			description: String?,
			imageKey: String?,
			region: String,
			minParticipants: Int,
			maxParticipants: Int,
			fee: GatheringFee,
			earlyBirdFee: GatheringFee?,
			earlyBirdCapacity: Int?,
			discountFee: GatheringFee?,
		): AdminGathering {
			validateGathering(title, description, region, minParticipants, maxParticipants)
			validateEarlyBirdCapacity(earlyBirdFee, earlyBirdCapacity, maxParticipants)
			return AdminGathering(
				type = type,
				title = title,
				description = description,
				imageKey = imageKey,
				region = region,
				minParticipants = minParticipants,
				maxParticipants = maxParticipants,
				fee = fee,
				earlyBirdFee = earlyBirdFee,
				earlyBirdCapacity = earlyBirdCapacity,
				discountFee = discountFee,
			)
		}

		private fun validateGathering(
			title: String,
			description: String?,
			region: String,
			minParticipants: Int,
			maxParticipants: Int,
		) {
			if (title.isBlank()) {
				throw AdminException(AdminErrorCode.GATHERING_INVALID_TITLE)
			}
			if (title.length > TITLE_MAX_LENGTH) {
				throw AdminException(AdminErrorCode.GATHERING_TITLE_TOO_LONG)
			}
			if (description != null && description.length > DESCRIPTION_MAX_LENGTH) {
				throw AdminException(AdminErrorCode.GATHERING_DESCRIPTION_TOO_LONG)
			}
			if (region.isBlank()) {
				throw AdminException(AdminErrorCode.GATHERING_INVALID_REGION)
			}
			if (minParticipants < MIN_PARTICIPANTS) {
				throw AdminException(AdminErrorCode.GATHERING_INVALID_MIN_PARTICIPANTS)
			}
			if (maxParticipants < minParticipants) {
				throw AdminException(AdminErrorCode.GATHERING_INVALID_MAX_PARTICIPANTS)
			}
		}

		// 얼리버드 특가 가격과 적용 인원은 세트다: 둘 다 있거나 둘 다 없어야 하고, 있으면 인원은 1..최대인원 범위여야 한다.
		private fun validateEarlyBirdCapacity(
			earlyBirdFee: GatheringFee?,
			earlyBirdCapacity: Int?,
			maxParticipants: Int,
		) {
			if ((earlyBirdFee == null) != (earlyBirdCapacity == null)) {
				throw AdminException(AdminErrorCode.GATHERING_INVALID_EARLY_BIRD_CAPACITY)
			}
			if (earlyBirdCapacity != null && (earlyBirdCapacity < 1 || earlyBirdCapacity > maxParticipants)) {
				throw AdminException(AdminErrorCode.GATHERING_INVALID_EARLY_BIRD_CAPACITY)
			}
		}
	}
}
