package com.org.meeple.api.admin.request

import com.org.meeple.admin.gathering.command.application.port.`in`.command.CreateAdminGatheringCommand
import com.org.meeple.common.gathering.GatheringType
import jakarta.validation.constraints.Future
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class CreateAdminGatheringRequest(
	@field:NotNull(message = "모임 종류는 필수입니다.")
	val type: GatheringType? = null,

	@field:NotBlank(message = "모임 제목은 필수입니다.")
	@field:Size(max = 100, message = "모임 제목은 100자 이하여야 합니다.")
	val title: String? = null,

	@field:Size(max = 1000, message = "모임 소개는 1000자 이하여야 합니다.")
	val description: String? = null,

	@field:Size(max = 512, message = "대표 이미지 URL은 512자 이하여야 합니다.")
	val imageUrl: String? = null,

	@field:NotBlank(message = "모임 지역은 필수입니다.")
	@field:Size(max = 100, message = "모임 지역은 100자 이하여야 합니다.")
	val region: String? = null,

	@field:NotNull(message = "모임 일시는 필수입니다.")
	@field:Future(message = "모임 일시는 현재 이후여야 합니다.")
	val gatheringAt: LocalDateTime? = null,

	@field:NotNull(message = "모임 정원은 필수입니다.")
	@field:Min(value = 2, message = "모임 정원은 최소 2명 이상이어야 합니다.")
	val capacity: Int? = null,

	// 정상가(남/녀, 필수)
	@field:NotNull(message = "남성 참가비는 필수입니다.")
	@field:PositiveOrZero(message = "참가비는 0원 이상이어야 합니다.")
	val maleFee: Int? = null,

	@field:NotNull(message = "여성 참가비는 필수입니다.")
	@field:PositiveOrZero(message = "참가비는 0원 이상이어야 합니다.")
	val femaleFee: Int? = null,

	// 얼리버드 특가(남/녀, 선택 — 남/녀를 함께 입력한다)
	@field:PositiveOrZero(message = "참가비는 0원 이상이어야 합니다.")
	val earlyBirdMaleFee: Int? = null,

	@field:PositiveOrZero(message = "참가비는 0원 이상이어야 합니다.")
	val earlyBirdFemaleFee: Int? = null,

	// 할인가(남/녀, 선택 — 남/녀를 함께 입력한다)
	@field:PositiveOrZero(message = "참가비는 0원 이상이어야 합니다.")
	val discountMaleFee: Int? = null,

	@field:PositiveOrZero(message = "참가비는 0원 이상이어야 합니다.")
	val discountFemaleFee: Int? = null,
) {
	fun toCommand(): CreateAdminGatheringCommand =
		CreateAdminGatheringCommand(
			type = type!!,
			title = title!!,
			description = description,
			imageUrl = imageUrl,
			region = region!!,
			gatheringAt = gatheringAt!!,
			capacity = capacity!!,
			maleFee = maleFee!!,
			femaleFee = femaleFee!!,
			earlyBirdMaleFee = earlyBirdMaleFee,
			earlyBirdFemaleFee = earlyBirdFemaleFee,
			discountMaleFee = discountMaleFee,
			discountFemaleFee = discountFemaleFee,
		)
}
