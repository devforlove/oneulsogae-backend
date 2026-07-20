package com.org.oneulsogae.admin.gathering.command.application.port.`in`.command

import com.org.oneulsogae.common.gathering.GatheringType

/**
 * 어드민 모임 전체 수정 입력. 전 필드를 새 값으로 교체한다.
 * 대표 이미지는 원시 바이트·메타로 받는다: [imageContent]가 있으면 교체하고, null이면 기존 이미지를 유지한다.
 */
data class UpdateAdminGatheringCommand(
	val type: GatheringType,
	val title: String,
	val description: String?,
	val imageContent: ByteArray?,
	val imageContentType: String?,
	val imageSize: Long,
	val region: String,
	val minParticipants: Int,
	val maxParticipants: Int,
)
