package com.org.meeple.admin.gathering.command.domain

import com.org.meeple.admin.common.error.AdminErrorCode
import com.org.meeple.admin.common.error.AdminException
import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.common.gathering.GatheringType
import java.time.LocalDateTime

private const val TITLE_MAX_LENGTH: Int = 100
private const val DESCRIPTION_MAX_LENGTH: Int = 1000
private const val MIN_CAPACITY: Int = 2

/**
 * 어드민(운영)이 등록하는 모임 도메인 모델(명령 측).
 * (admin은 core에 의존하지 않으므로 core Gathering을 쓰지 않고 자체 모델을 둔다)
 * 운영이 만든 모임이므로 생성자(userId)는 두지 않는다. 영속성에서 user_id는 null(운영 생성)로 저장된다.
 * 참가비는 성별로 나뉜 값 객체([GatheringFee])로 표현한다: 정상가([fee], 필수),
 * 얼리버드 특가([earlyBirdFee], 선택)·할인가([discountFee], 선택)는 해당 특가가 있는 모임만 값을 가진다.
 * 생성 시 status는 RECRUITING(모집중)이다. 영속성은 [com.org.meeple.infra.gathering.command.entity.GatheringEntity]가 담당한다.
 */
data class AdminGathering(
	val id: Long = 0,
	val type: GatheringType,
	val title: String,
	val description: String?,
	val imageKey: String?,
	val region: String,
	val gatheringAt: LocalDateTime,
	val capacity: Int,
	val fee: GatheringFee,
	val earlyBirdFee: GatheringFee?,
	val discountFee: GatheringFee?,
	val status: GatheringStatus = GatheringStatus.RECRUITING,
) {
	companion object {

		/**
		 * [type]·[title]·[description]·[region]·[gatheringAt]·[capacity]·참가비([fee]·[earlyBirdFee]·[discountFee])로 모임을 만든다.
		 * 입력을 검증한 뒤 모집중(RECRUITING)으로 만든다. [now]는 모임 일시가 미래인지 판정하는 기준 시각이다.
		 */
		fun create(
			type: GatheringType,
			title: String,
			description: String?,
			imageKey: String?,
			region: String,
			gatheringAt: LocalDateTime,
			capacity: Int,
			fee: GatheringFee,
			earlyBirdFee: GatheringFee?,
			discountFee: GatheringFee?,
			now: LocalDateTime,
		): AdminGathering {
			validateGathering(title, description, region, capacity, gatheringAt, now)
			return AdminGathering(
				type = type,
				title = title,
				description = description,
				imageKey = imageKey,
				region = region,
				gatheringAt = gatheringAt,
				capacity = capacity,
				fee = fee,
				earlyBirdFee = earlyBirdFee,
				discountFee = discountFee,
			)
		}

		private fun validateGathering(
			title: String,
			description: String?,
			region: String,
			capacity: Int,
			gatheringAt: LocalDateTime,
			now: LocalDateTime,
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
			if (capacity < MIN_CAPACITY) {
				throw AdminException(AdminErrorCode.GATHERING_INVALID_CAPACITY)
			}
			if (!gatheringAt.isAfter(now)) {
				throw AdminException(AdminErrorCode.GATHERING_INVALID_GATHERING_AT)
			}
		}
	}
}
