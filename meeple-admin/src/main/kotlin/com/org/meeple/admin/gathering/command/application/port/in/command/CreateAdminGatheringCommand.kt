package com.org.meeple.admin.gathering.command.application.port.`in`.command

import com.org.meeple.common.gathering.GatheringType

/**
 * 어드민 모임 생성 입력. (운영 생성이므로 생성자 userId는 없음 — 저장 시 null)
 * 대표 이미지는 원시 바이트·메타로 받는다(없으면 [imageContent]가 null). 서비스가 검증 후 S3에 올리고 키만 저장한다.
 * (admin 도메인이 웹 타입(MultipartFile)에 의존하지 않도록 컨트롤러가 값을 뽑아 넘긴다)
 */
data class CreateAdminGatheringCommand(
	val type: GatheringType,
	val title: String,
	val description: String?,
	val imageContent: ByteArray?,
	val imageContentType: String?,
	val imageSize: Long,
	val region: String,
	val minParticipants: Int,
	val maxParticipants: Int,
	// 정상가(남/녀, 필수)
	val maleFee: Int,
	val femaleFee: Int,
	// 얼리버드 특가(남/녀, 선택) — 가격이 있으면 적용 인원(earlyBirdCapacity)도 함께
	val earlyBirdMaleFee: Int?,
	val earlyBirdFemaleFee: Int?,
	val earlyBirdCapacity: Int?,
	// 할인가(남/녀, 선택)
	val discountMaleFee: Int?,
	val discountFemaleFee: Int?,
)
