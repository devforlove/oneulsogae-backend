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
 * 주최자는 [userId]로만 표현하며 참가자는 별도 모델([com.org.meeple.core.gathering.command.domain.GatheringMember] 등 향후)에서 다룬다.
 * 생성 시 status는 RECRUITING(모집중)이다. 영속성은 [com.org.meeple.infra.gathering.command.entity.GatheringEntity]가 담당한다.
 */
data class Gathering(
	val id: Long = 0,
	val type: GatheringType,
	val userId: Long?,
	val title: String,
	val description: String?,
	val regionId: Long,
	val gatheringAt: LocalDateTime,
	val capacity: Int,
	val fee: Int,
	val status: GatheringStatus = GatheringStatus.RECRUITING,
) {
	companion object {

		/**
		 * 생성자([userId], 운영 생성이면 null)가 [type]·[title]·[description]·[gatheringAt]·[capacity]로 모임을 만든다.
		 * 입력을 검증한 뒤 모집중(RECRUITING)으로 만든다. [now]는 모임 일시가 미래인지 판정하는 기준 시각이다.
		 */
		fun create(
			userId: Long?,
			type: GatheringType,
			title: String,
			description: String?,
			regionId: Long,
			gatheringAt: LocalDateTime,
			capacity: Int,
			fee: Int,
			now: LocalDateTime,
		): Gathering {
			validateGathering(title, description, capacity, fee, gatheringAt, now)
			return Gathering(
				type = type,
				userId = userId,
				title = title,
				description = description,
				regionId = regionId,
				gatheringAt = gatheringAt,
				capacity = capacity,
				fee = fee,
			)
		}

		private fun validateGathering(
			title: String,
			description: String?,
			capacity: Int,
			fee: Int,
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
			if (capacity < MIN_CAPACITY) {
				throw BusinessException(GatheringErrorCode.INVALID_CAPACITY)
			}
			if (fee < 0) {
				throw BusinessException(GatheringErrorCode.INVALID_FEE)
			}
			if (!gatheringAt.isAfter(now)) {
				throw BusinessException(GatheringErrorCode.INVALID_GATHERING_AT)
			}
		}
	}
}
