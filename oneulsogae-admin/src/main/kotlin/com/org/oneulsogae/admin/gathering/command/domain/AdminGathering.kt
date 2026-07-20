package com.org.oneulsogae.admin.gathering.command.domain

import com.org.oneulsogae.admin.common.error.AdminErrorCode
import com.org.oneulsogae.admin.common.error.AdminException
import com.org.oneulsogae.common.gathering.GatheringStatus
import com.org.oneulsogae.common.gathering.GatheringType

private const val TITLE_MAX_LENGTH: Int = 100
private const val DESCRIPTION_MAX_LENGTH: Int = 4000
private const val MIN_PARTICIPANTS: Int = 2

/**
 * 어드민(운영)이 등록하는 모임 도메인 모델(명령 측).
 * (admin은 core에 의존하지 않으므로 core Gathering을 쓰지 않고 자체 모델을 둔다)
 * 운영이 만든 모임이므로 생성자(userId)는 두지 않는다. 영속성에서 user_id는 null(운영 생성)로 저장된다.
 * 인원은 [minParticipants](최소 성사 인원)·[maxParticipants](정원)로 표현한다.
 * 참가비는 모임이 아니라 일정([GatheringSchedule])이 가진다.
 * 생성 시 status는 활성화(RECRUITING)이고, 취소(CANCELED)로만 전이한다. 영속성은 [com.org.oneulsogae.infra.gathering.command.entity.GatheringEntity]가 담당한다.
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
	// 등록 직후는 활성화(RECRUITING). 취소(CANCELED)로만 전이한다.
	val status: GatheringStatus = GatheringStatus.RECRUITING,
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
	): AdminGathering {
		validateGathering(title, description, region, minParticipants, maxParticipants)
		return copy(
			type = type,
			title = title,
			description = description,
			imageKey = imageKey,
			region = region,
			minParticipants = minParticipants,
			maxParticipants = maxParticipants,
		)
	}

	companion object {

		/**
		 * [type]·[title]·[description]·[imageKey]·[region]·인원([minParticipants]·[maxParticipants])로 모임을 만든다.
		 * 입력을 검증한 뒤 활성화(RECRUITING)로 만든다.
		 */
		fun create(
			type: GatheringType,
			title: String,
			description: String?,
			imageKey: String?,
			region: String,
			minParticipants: Int,
			maxParticipants: Int,
		): AdminGathering {
			validateGathering(title, description, region, minParticipants, maxParticipants)
			return AdminGathering(
				type = type,
				title = title,
				description = description,
				imageKey = imageKey,
				region = region,
				minParticipants = minParticipants,
				maxParticipants = maxParticipants,
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
	}
}
