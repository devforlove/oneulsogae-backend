package com.org.oneulsogae.api.admin.request

import com.org.oneulsogae.admin.gathering.command.application.port.`in`.command.CreateAdminGatheringCommand
import com.org.oneulsogae.common.gathering.GatheringType
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class CreateAdminGatheringRequest(
	@field:NotNull(message = "모임 종류는 필수입니다.")
	val type: GatheringType? = null,

	@field:NotBlank(message = "모임 제목은 필수입니다.")
	@field:Size(max = 100, message = "모임 제목은 100자 이하여야 합니다.")
	val title: String? = null,

	@field:Size(max = 4000, message = "모임 소개는 4000자 이하여야 합니다.")
	val description: String? = null,

	@field:NotBlank(message = "모임 지역은 필수입니다.")
	@field:Size(max = 100, message = "모임 지역은 100자 이하여야 합니다.")
	val region: String? = null,

	@field:NotNull(message = "모임 최소 인원은 필수입니다.")
	@field:Min(value = 2, message = "모임 최소 인원은 2명 이상이어야 합니다.")
	val minParticipants: Int? = null,

	@field:NotNull(message = "모임 최대 인원은 필수입니다.")
	@field:Min(value = 2, message = "모임 최대 인원은 2명 이상이어야 합니다.")
	val maxParticipants: Int? = null,
) {
	/** 대표 이미지(선택)는 컨트롤러가 MultipartFile에서 뽑아 넘긴다. (없으면 [imageContent]가 null) */
	fun toCommand(imageContent: ByteArray?, imageContentType: String?, imageSize: Long): CreateAdminGatheringCommand =
		CreateAdminGatheringCommand(
			type = type!!,
			title = title!!,
			description = description,
			imageContent = imageContent,
			imageContentType = imageContentType,
			imageSize = imageSize,
			region = region!!,
			minParticipants = minParticipants!!,
			maxParticipants = maxParticipants!!,
		)
}
