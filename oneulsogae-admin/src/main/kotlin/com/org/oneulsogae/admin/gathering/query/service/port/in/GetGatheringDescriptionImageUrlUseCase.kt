package com.org.oneulsogae.admin.gathering.query.service.port.`in`

fun interface GetGatheringDescriptionImageUrlUseCase {
	/** 소개 이미지 key를 presigned GET URL로. 소개 이미지 프리픽스가 아니면 null(→ 404). */
	fun execute(key: String): String?
}
