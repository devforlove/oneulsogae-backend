package com.org.meeple.core.gathering.command.domain

import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.common.gathering.GatheringType
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.gathering.GatheringErrorCode
import java.time.LocalDateTime

private const val TITLE_MAX_LENGTH: Int = 100
private const val DESCRIPTION_MAX_LENGTH: Int = 1000
private const val MIN_CAPACITY: Int = 2

/**
 * 모임 도메인 모델. (명령 측 — 생성/저장에 쓴다)
 * [userId]는 모임을 만든 생성자다. **null이면 운영(관리자) 생성, 값이 있으면 해당 유저 생성**이다.
 * 참가비는 성별로 나뉜 값 객체([GatheringFee])로 표현한다: 정상가([fee], 필수),
 * 얼리버드 특가([earlyBirdFee], 선택)·할인가([discountFee], 선택)는 해당 특가가 있는 모임만 값을 가진다.
 * 생성 시 status는 RECRUITING(모집중)이다. 영속성은 [com.org.meeple.infra.gathering.command.entity.GatheringEntity]가 담당한다.
 */
data class Gathering(
	val id: Long = 0,
	val type: GatheringType,
	val userId: Long?,
	val title: String,
	val description: String?,
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
		 * 생성자([userId], 운영 생성이면 null)가 [type]·[title]·[description]·[region]·[gatheringAt]·[capacity]·
		 * 참가비([fee]·[earlyBirdFee]·[discountFee])로 모임을 만든다.
		 * 입력을 검증한 뒤 모집중(RECRUITING)으로 만든다. [now]는 모임 일시가 미래인지 판정하는 기준 시각이다.
		 */
		fun create(
			userId: Long?,
			type: GatheringType,
			title: String,
			description: String?,
			region: String,
			gatheringAt: LocalDateTime,
			capacity: Int,
			fee: GatheringFee,
			earlyBirdFee: GatheringFee?,
			discountFee: GatheringFee?,
			now: LocalDateTime,
		): Gathering {
			validateGathering(title, description, region, capacity, gatheringAt, now)
			return Gathering(
				type = type,
				userId = userId,
				title = title,
				description = description,
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
				throw BusinessException(GatheringErrorCode.INVALID_TITLE)
			}
			if (title.length > TITLE_MAX_LENGTH) {
				throw BusinessException(GatheringErrorCode.TITLE_TOO_LONG)
			}
			if (description != null && description.length > DESCRIPTION_MAX_LENGTH) {
				throw BusinessException(GatheringErrorCode.DESCRIPTION_TOO_LONG)
			}
			if (region.isBlank()) {
				throw BusinessException(GatheringErrorCode.INVALID_REGION)
			}
			if (capacity < MIN_CAPACITY) {
				throw BusinessException(GatheringErrorCode.INVALID_CAPACITY)
			}
			if (!gatheringAt.isAfter(now)) {
				throw BusinessException(GatheringErrorCode.INVALID_GATHERING_AT)
			}
		}
	}
}
