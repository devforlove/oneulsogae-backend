package com.org.meeple.api.offline.response

import com.org.meeple.common.gathering.GatheringType
import com.org.meeple.core.gathering.query.dto.GatheringDetailView
import java.time.LocalDateTime

/**
 * 오프라인 모임 상세 응답. 소개·인원·참가비 3티어(정상·얼리버드·할인)까지 flat하게 내려준다.
 * imageKey는 응답에서 제외하고 presigned [imageUrl]만 노출한다. (모집중 모임만 조회되므로 상태는 포함하지 않는다)
 */
data class GatheringDetailResponse(
	val id: Long,
	val type: GatheringType,
	val typeDescription: String,
	val title: String,
	val description: String?,
	val imageUrl: String?,
	val region: String,
	val gatheringAt: LocalDateTime,
	val minParticipants: Int,
	val maxParticipants: Int,
	val maleFee: Int,
	val femaleFee: Int,
	val earlyBirdMaleFee: Int?,
	val earlyBirdFemaleFee: Int?,
	val earlyBirdCapacity: Int?,
	val discountMaleFee: Int?,
	val discountFemaleFee: Int?,
) {

	companion object {

		fun of(view: GatheringDetailView): GatheringDetailResponse =
			GatheringDetailResponse(
				id = view.id,
				type = view.type,
				typeDescription = view.type.description,
				title = view.title,
				description = view.description,
				imageUrl = view.imageUrl,
				region = view.region,
				gatheringAt = view.gatheringAt,
				minParticipants = view.minParticipants,
				maxParticipants = view.maxParticipants,
				maleFee = view.maleFee,
				femaleFee = view.femaleFee,
				earlyBirdMaleFee = view.earlyBirdMaleFee,
				earlyBirdFemaleFee = view.earlyBirdFemaleFee,
				earlyBirdCapacity = view.earlyBirdCapacity,
				discountMaleFee = view.discountMaleFee,
				discountFemaleFee = view.discountFemaleFee,
			)
	}
}
