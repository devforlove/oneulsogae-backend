package com.org.meeple.api.admin.response

import com.org.meeple.admin.gathering.query.dto.AdminGatheringDetailView
import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.common.gathering.GatheringType
import java.time.LocalDateTime

/** 어드민 모임 상세 응답. 목록 필드 + 소개·참가비 상세(성별·티어별, 없는 티어는 null). */
data class AdminGatheringDetailResponse(
	val id: Long,
	val type: GatheringType,
	val title: String,
	val description: String?,
	val imageUrl: String?,
	val region: String,
	val minParticipants: Int,
	val maxParticipants: Int,
	val maleFee: Int,
	val femaleFee: Int,
	val earlyBirdMaleFee: Int?,
	val earlyBirdFemaleFee: Int?,
	val earlyBirdCapacity: Int?,
	val discountMaleFee: Int?,
	val discountFemaleFee: Int?,
	val status: GatheringStatus,
	val createdAt: LocalDateTime?,
) {
	companion object {
		fun of(view: AdminGatheringDetailView): AdminGatheringDetailResponse =
			AdminGatheringDetailResponse(
				id = view.id,
				type = view.type,
				title = view.title,
				description = view.description,
				imageUrl = view.imageUrl,
				region = view.region,
				minParticipants = view.minParticipants,
				maxParticipants = view.maxParticipants,
				maleFee = view.maleFee,
				femaleFee = view.femaleFee,
				earlyBirdMaleFee = view.earlyBirdMaleFee,
				earlyBirdFemaleFee = view.earlyBirdFemaleFee,
				earlyBirdCapacity = view.earlyBirdCapacity,
				discountMaleFee = view.discountMaleFee,
				discountFemaleFee = view.discountFemaleFee,
				status = view.status,
				createdAt = view.createdAt,
			)
	}
}
