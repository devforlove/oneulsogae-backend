package com.org.meeple.core.image.query.dto

/**
 * 이미지 템플릿 한 건의 조회 전용 read model.
 * [code]로 식별하며, 소비처(팝업 등)는 이 [imageUrl]/[imageWidth]/[imageHeight]로 이미지를 렌더한다.
 */
data class ImageTemplateView(
	val code: String,
	val imageUrl: String,
	val imageWidth: Int,
	val imageHeight: Int,
)
