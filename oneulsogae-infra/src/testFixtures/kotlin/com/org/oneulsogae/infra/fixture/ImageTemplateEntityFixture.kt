package com.org.oneulsogae.infra.fixture

import com.org.oneulsogae.infra.image.entity.ImageTemplateEntity

/**
 * [ImageTemplateEntity] 테스트 픽스처. [code]로 참조되는 이미지(URL·치수)를 만든다.
 */
object ImageTemplateEntityFixture {

	fun create(
		code: String,
		imageUrl: String = "https://placehold.co/320x400",
		imageWidth: Int = 320,
		imageHeight: Int = 400,
	): ImageTemplateEntity =
		ImageTemplateEntity(
			code = code,
			imageUrl = imageUrl,
			imageWidth = imageWidth,
			imageHeight = imageHeight,
		)
}
