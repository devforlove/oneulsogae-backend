package com.org.oneulsogae.domain.lounge

import com.org.oneulsogae.core.lounge.command.domain.LoungePostImages
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [LoungePostImages] 도메인 유닛 테스트.
 * 업로드한 순서가 그대로 노출 순서(displayOrder)로 굳는지 확인한다.
 */
class LoungePostImagesTest : DescribeSpec({

	describe("of") {
		it("업로드 순서대로 0부터 노출 순서를 매긴다") {
			val images: LoungePostImages = LoungePostImages.of(
				postId = 7L,
				imageKeys = listOf("lounge-posts/1/a.jpg", "lounge-posts/1/b.png"),
			)

			images.values.size shouldBe 2
			images.values[0].postId shouldBe 7L
			images.values[0].imageKey shouldBe "lounge-posts/1/a.jpg"
			images.values[0].displayOrder shouldBe 0
			images.values[1].imageKey shouldBe "lounge-posts/1/b.png"
			images.values[1].displayOrder shouldBe 1
		}

		it("사진이 없으면 빈 목록이다") {
			LoungePostImages.of(postId = 7L, imageKeys = emptyList()).values shouldBe emptyList()
		}
	}
})
