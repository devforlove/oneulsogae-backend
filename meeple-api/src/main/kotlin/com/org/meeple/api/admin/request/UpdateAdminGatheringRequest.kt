package com.org.meeple.api.admin.request

import com.org.meeple.admin.gathering.command.application.port.`in`.command.UpdateAdminGatheringCommand
import com.org.meeple.common.gathering.GatheringType
import jakarta.validation.constraints.Future
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * 어드민 모임 전체 수정 요청. 필드는 생성과 동일하다(전체 교체).
 * 대표 이미지는 image 파트(선택)로 받으며, 없으면 기존 이미지를 유지한다.
 */
data class UpdateAdminGatheringRequest(
	@field:NotNull(message = "모임 종류는 필수입니다.")
	val type: GatheringType? = null,

	@field:NotBlank(message = "모임 제목은 필수입니다.")
	@field:Size(max = 100, message = "모임 제목은 100자 이하여야 합니다.")
	val title: String? = null,

	@field:Size(max = 1000, message = "모임 소개는 1000자 이하여야 합니다.")
	val description: String? = null,

	@field:NotBlank(message = "모임 지역은 필수입니다.")
	@field:Size(max = 100, message = "모임 지역은 100자 이하여야 합니다.")
	val region: String? = null,

	@field:NotNull(message = "모임 일시는 필수입니다.")
	@field:Future(message = "모임 일시는 현재 이후여야 합니다.")
	val gatheringAt: LocalDateTime? = null,

	@field:NotNull(message = "모임 최소 인원은 필수입니다.")
	@field:Min(value = 2, message = "모임 최소 인원은 2명 이상이어야 합니다.")
	val minParticipants: Int? = null,

	@field:NotNull(message = "모임 최대 인원은 필수입니다.")
	@field:Min(value = 2, message = "모임 최대 인원은 2명 이상이어야 합니다.")
	val maxParticipants: Int? = null,

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

	@field:Min(value = 1, message = "얼리버드 적용 인원은 1명 이상이어야 합니다.")
	val earlyBirdCapacity: Int? = null,

	// 할인가(남/녀, 선택 — 남/녀를 함께 입력한다)
	@field:PositiveOrZero(message = "참가비는 0원 이상이어야 합니다.")
	val discountMaleFee: Int? = null,

	@field:PositiveOrZero(message = "참가비는 0원 이상이어야 합니다.")
	val discountFemaleFee: Int? = null,
) {
	/** 대표 이미지(선택)는 컨트롤러가 MultipartFile에서 뽑아 넘긴다. (없으면 [imageContent]가 null → 기존 이미지 유지) */
	fun toCommand(imageContent: ByteArray?, imageContentType: String?, imageSize: Long): UpdateAdminGatheringCommand =
		UpdateAdminGatheringCommand(
			type = type!!,
			title = title!!,
			description = description,
			imageContent = imageContent,
			imageContentType = imageContentType,
			imageSize = imageSize,
			region = region!!,
			gatheringAt = gatheringAt!!,
			minParticipants = minParticipants!!,
			maxParticipants = maxParticipants!!,
			maleFee = maleFee!!,
			femaleFee = femaleFee!!,
			earlyBirdMaleFee = earlyBirdMaleFee,
			earlyBirdFemaleFee = earlyBirdFemaleFee,
			earlyBirdCapacity = earlyBirdCapacity,
			discountMaleFee = discountMaleFee,
			discountFemaleFee = discountFemaleFee,
		)
}
