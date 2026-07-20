package com.org.oneulsogae.api.lounge

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.LoungePostEntityFixture
import com.org.oneulsogae.infra.fixture.LoungePostImageEntityFixture
import com.org.oneulsogae.infra.fixture.RegionEntityFixture
import com.org.oneulsogae.infra.fixture.SelfIntroPostEntityFixture
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.lounge.command.entity.LoungePostEntity
import com.org.oneulsogae.infra.region.entity.RegionEntity
import io.restassured.RestAssured
import org.hamcrest.Matchers
import java.time.LocalDate
import java.time.Period

/**
 * `GET /lounge/v1/self-intro-posts/{postId}` E2E 테스트.
 * 상세가 작성자 프로필(닉네임·성별·만 나이·키·활동지역·직업)과 본문 7개 항목, 사진 전체(노출 순서),
 * 좋아요 수를 내려주는지와 없는 글이 404(LOUNGE-008)로 막히는지 검증한다.
 * (presigned URL은 [com.org.oneulsogae.common.config.TestFileStorageConfig]의 페이크로 대체)
 */
class GetSelfIntroPostDetailE2ETest : AbstractIntegrationSupport({

	describe("GET /lounge/v1/self-intro-posts/{postId}") {

		context("사진 2장이 붙은 셀소를 조회하면") {
			it("프로필·본문·사진 목록·좋아요 수를 노출 순서대로 내려준다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-detail-1")).id!!
				val region: RegionEntity = IntegrationUtil.persist(
					RegionEntityFixture.create(sido = "서울특별시", sigungu = "마포구"),
				)
				val birthday: LocalDate = LocalDate.of(1996, 1, 1)
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(
						userId = userId,
						nickname = "상세유저",
						gender = Gender.FEMALE,
						birthday = birthday,
						height = 165,
						job = "디자이너",
						regionId = region.id,
					),
				)
				val post: LoungePostEntity = IntegrationUtil.persist(
					LoungePostEntityFixture.create(userId = userId, likeCount = 7),
				)
				IntegrationUtil.persist(SelfIntroPostEntityFixture.create(postId = post.id!!))
				IntegrationUtil.persist(
					LoungePostImageEntityFixture.create(
						postId = post.id!!,
						imageKey = "lounge-posts/$userId/second.png",
						displayOrder = 1,
					),
				)
				IntegrationUtil.persist(
					LoungePostImageEntityFixture.create(
						postId = post.id!!,
						imageKey = "lounge-posts/$userId/first.jpg",
						displayOrder = 0,
					),
				)
				val expectedAge: Int = Period.between(birthday, LocalDate.now()).years

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.get("/lounge/v1/self-intro-posts/${post.id}")
					.then()
					.statusCode(200)
					.body("success", Matchers.equalTo(true))
					.body("data.postId", Matchers.equalTo(post.id!!.toInt()))
					.body("data.authorNickname", Matchers.equalTo("상세유저"))
					.body("data.likeCount", Matchers.equalTo(7))
					.body("data.gender", Matchers.equalTo("FEMALE"))
					.body("data.age", Matchers.equalTo(expectedAge))
					.body("data.height", Matchers.equalTo(165))
					.body("data.activityArea", Matchers.equalTo("서울특별시 마포구"))
					.body("data.job", Matchers.equalTo("디자이너"))
					.body("data.mbti", Matchers.equalTo("ENFP"))
					.body("data.freeWord", Matchers.equalTo("편하게 연락 주세요"))
					.body("data.imageUrls", Matchers.hasSize<Any>(2))
					.body("data.imageUrls[0]", Matchers.equalTo("https://presigned.test/lounge-posts/$userId/first.jpg"))
					.body("data.imageUrls[1]", Matchers.equalTo("https://presigned.test/lounge-posts/$userId/second.png"))
			}
		}

		context("없는 글을 조회하면") {
			it("404(LOUNGE-008)를 반환한다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-detail-2")).id!!

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.get("/lounge/v1/self-intro-posts/99999999")
					.then()
					.statusCode(404)
					.body("success", Matchers.equalTo(false))
					.body("error.code", Matchers.equalTo("LOUNGE-008"))
			}
		}
	}
})
