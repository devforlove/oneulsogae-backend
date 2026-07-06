package com.org.meeple.core.gathering.query.dto

import com.org.meeple.common.gathering.GatheringType
import java.time.LocalDateTime

/**
 * 유저용 모임 상세 한 건(read model). 목록과 달리 소개·인원·참가비 3티어까지 포함한다.
 * dao는 [imageKey]까지 채우고 [imageUrl]은 null로 둔다. 서비스가 presign 결과로 [imageUrl]을 채운다(이미지 없으면 null).
 * 노출 대상은 모집중(RECRUITING) 모임뿐이라 상태(status)는 표시하지 않는다(그 외 상태는 조회 시 404).
 */
data class GatheringDetailView(
	val id: Long,
	val type: GatheringType,
	val title: String,
	val description: String?,
	val imageKey: String?,
	val imageUrl: String? = null,
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
	/** dao 투영용 생성자. imageUrl은 서비스가 presign으로 채운다. */
	constructor(
		id: Long,
		type: GatheringType,
		title: String,
		description: String?,
		imageKey: String?,
		region: String,
		gatheringAt: LocalDateTime,
		minParticipants: Int,
		maxParticipants: Int,
		maleFee: Int,
		femaleFee: Int,
		earlyBirdMaleFee: Int?,
		earlyBirdFemaleFee: Int?,
		earlyBirdCapacity: Int?,
		discountMaleFee: Int?,
		discountFemaleFee: Int?,
	) : this(
		id, type, title, description, imageKey, null, region, gatheringAt,
		minParticipants, maxParticipants, maleFee, femaleFee,
		earlyBirdMaleFee, earlyBirdFemaleFee, earlyBirdCapacity, discountMaleFee, discountFemaleFee,
	)
}
