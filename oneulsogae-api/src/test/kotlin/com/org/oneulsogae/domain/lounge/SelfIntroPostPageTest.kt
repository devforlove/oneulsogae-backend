package com.org.oneulsogae.domain.lounge

import com.org.oneulsogae.core.lounge.query.dto.SelfIntroPostPage
import com.org.oneulsogae.core.lounge.query.dto.SelfIntroPostView
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [SelfIntroPostPage] 유닛 테스트.
 * "한 건 더 읽기"로 다음 페이지 존재를 판정하고 커서를 산출하는 규칙, 대표 사진 URL 채우기를 확인한다.
 */
class SelfIntroPostPageTest : DescribeSpec({

	fun view(postId: Long, imageKey: String? = "lounge-posts/1/$postId.jpg"): SelfIntroPostView =
		SelfIntroPostView(postId = postId, authorNickname = "닉네임", likeCount = 3, imageKey = imageKey)

	describe("of") {
		it("size보다 많이 읽었으면 초과분을 잘라내고 다음 페이지가 있다고 본다") {
			val page: SelfIntroPostPage = SelfIntroPostPage.of(listOf(view(3), view(2), view(1)), size = 2)

			page.values.map { it.postId } shouldBe listOf(3L, 2L)
			page.hasNext shouldBe true
			page.nextCursor shouldBe 2L
		}

		it("size 이하로 읽었으면 마지막 페이지이고 커서는 null이다") {
			val page: SelfIntroPostPage = SelfIntroPostPage.of(listOf(view(3), view(2)), size = 2)

			page.values.size shouldBe 2
			page.hasNext shouldBe false
			page.nextCursor shouldBe null
		}

		it("빈 목록도 마지막 페이지다") {
			val page: SelfIntroPostPage = SelfIntroPostPage.of(emptyList(), size = 24)

			page.values shouldBe emptyList()
			page.hasNext shouldBe false
			page.nextCursor shouldBe null
		}
	}

	describe("withImageUrls") {
		it("대표 사진 키를 열람용 URL로 바꾸고, 사진이 없으면 null로 둔다") {
			val page: SelfIntroPostPage = SelfIntroPostPage
				.of(listOf(view(2), view(1, imageKey = null)), size = 24)
				.withImageUrls { imageKey: String -> "https://presigned.test/$imageKey" }

			page.values[0].imageUrl shouldBe "https://presigned.test/lounge-posts/1/2.jpg"
			page.values[1].imageUrl shouldBe null
		}
	}
})
