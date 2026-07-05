package com.org.meeple.api.gathering.request

import com.org.meeple.common.gathering.GatheringType
import com.org.meeple.core.gathering.command.application.port.`in`.command.CreateGatheringCommand
import jakarta.validation.constraints.Future
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class CreateGatheringRequest(
	@field:NotNull(message = "모임 종류는 필수입니다.")
	val type: GatheringType? = null,

	@field:NotBlank(message = "모임 제목은 필수입니다.")
	@field:Size(max = 100, message = "모임 제목은 100자 이하여야 합니다.")
	val title: String? = null,

	@field:Size(max = 1000, message = "모임 소개는 1000자 이하여야 합니다.")
	val description: String? = null,

	@field:NotNull(message = "모임 일시는 필수입니다.")
	@field:Future(message = "모임 일시는 현재 이후여야 합니다.")
	val gatheringAt: LocalDateTime? = null,

	@field:NotNull(message = "모임 정원은 필수입니다.")
	@field:Min(value = 2, message = "모임 정원은 최소 2명 이상이어야 합니다.")
	val capacity: Int? = null,
) {
	fun toCommand(userId: Long?): CreateGatheringCommand =
		CreateGatheringCommand(
			userId = userId,
			type = type!!,
			title = title!!,
			description = description,
			gatheringAt = gatheringAt!!,
			capacity = capacity!!,
		)
}
