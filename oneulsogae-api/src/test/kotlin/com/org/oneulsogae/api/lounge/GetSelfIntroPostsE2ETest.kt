package com.org.oneulsogae.api.lounge

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.LoungePostEntityFixture
import com.org.oneulsogae.infra.fixture.LoungePostImageEntityFixture
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.lounge.command.entity.LoungePostEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungePostEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungePostImageEntity
import io.restassured.RestAssured
import org.hamcrest.Matchers

/**
 * `GET /lounge/v1/self-intro-posts` E2E 테스트.
 * 라운지 그리드용 목록을 최신순 24개씩 커서 페이징으로 내려주는지, 좋아요 수·작성자 닉네임·
 * 대표 사진 열람용 URL이 실리는지, 사진이 없는 글도 빠지지 않는지 검증한다.
 * (presigned URL은 [com.org.oneulsogae.common.config.TestFileStorageConfig]의 페이크로 대체)
 */
class GetSelfIntroPostsE2ETest : AbstractIntegrationSupport({

	beforeSpec {
		IntegrationUtil.deleteAll(QLoungePostImageEntity.loungePostImageEntity)
		IntegrationUtil.deleteAll(QLoungePostEntity.loungePostEntity)
	}

	describe("GET /lounge/v1/self-intro-posts") {

		context("셀소 26건이 있으면") {
			it("최신순 24건과 다음 커서를 내려주고, 커서로 나머지 2건을 잇는다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-list-1")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, nickname = "라운지주민"))
				val postIds: List<Long> = (1..26).map { index: Int ->
					val post: LoungePostEntity = IntegrationUtil.persist(
						LoungePostEntityFixture.create(userId = userId, likeCount = index),
					)
					IntegrationUtil.persist(
						LoungePostImageEntityFixture.create(
							postId = post.id!!,
							imageKey = "lounge-posts/$userId/$index.jpg",
						),
					)
					post.id!!
				}
				val latestPostId: Long = postIds.last()
				val cursorPostId: Long = postIds[postIds.size - 24] // 첫 페이지의 마지막(24번째) 글

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.get("/lounge/v1/self-intro-posts")
					.then()
					.statusCode(200)
					.body("success", Matchers.equalTo(true))
					.body("data.items", Matchers.hasSize<Any>(24))
					.body("data.hasNext", Matchers.equalTo(true))
					.body("data.nextCursor", Matchers.equalTo(cursorPostId.toInt()))
					.body("data.items[0].postId", Matchers.equalTo(latestPostId.toInt()))
					.body("data.items[0].authorNickname", Matchers.equalTo("라운지주민"))
					.body("data.items[0].likeCount", Matchers.equalTo(26))
					.body("data.items[0].imageUrl", Matchers.equalTo("https://presigned.test/lounge-posts/$userId/26.jpg"))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.queryParam("cursor", cursorPostId)
					.get("/lounge/v1/self-intro-posts")
					.then()
					.statusCode(200)
					.body("data.items", Matchers.hasSize<Any>(2))
					.body("data.hasNext", Matchers.equalTo(false))
					.body("data.nextCursor", Matchers.nullValue())
					.body("data.items[0].postId", Matchers.equalTo(postIds[1].toInt()))
			}
		}

		context("사진이 없는 글이 있으면") {
			it("목록에서 빠지지 않고 imageUrl만 null로 내려간다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-list-2")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, nickname = "사진없음"))
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = userId))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.get("/lounge/v1/self-intro-posts")
					.then()
					.statusCode(200)
					.body("data.items[0].postId", Matchers.equalTo(post.id!!.toInt()))
					.body("data.items[0].authorNickname", Matchers.equalTo("사진없음"))
					.body("data.items[0].imageUrl", Matchers.nullValue())
			}
		}
	}
})
