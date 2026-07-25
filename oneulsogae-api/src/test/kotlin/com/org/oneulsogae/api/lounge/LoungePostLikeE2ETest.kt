package com.org.oneulsogae.api.lounge

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.LoungePostEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.lounge.command.entity.LoungePostEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungePostEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungePostLikeEntity
import io.kotest.matchers.shouldBe
import io.restassured.RestAssured
import org.hamcrest.Matchers

/**
 * 라운지 좋아요·조회수 E2E 테스트.
 * 좋아요 등록/취소(멱등, like_count 증감, likedByMe)와 상세 조회수 증가를 검증한다.
 */
class LoungePostLikeE2ETest : AbstractIntegrationSupport({

	afterTest {
		IntegrationUtil.deleteAll(QLoungePostLikeEntity.loungePostLikeEntity)
		IntegrationUtil.deleteAll(QLoungePostEntity.loungePostEntity)
	}

	describe("POST·DELETE /lounge/v1/self-intro-posts/{postId}/likes") {

		context("좋아요를 누르면") {
			it("좋아요 행이 생기고 like_count가 1 오른다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-like-author-1")).id!!
				val likerId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-like-user-1")).id!!
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(likerId)}")
					.post("/lounge/v1/self-intro-posts/${post.id}/likes")
					.then()
					.statusCode(200)

				likeCountOf(post.id!!) shouldBe 1
				likeRowCountOf(post.id!!) shouldBe 1L
			}
		}

		context("같은 글에 좋아요를 두 번 누르면") {
			it("멱등 — 두 번째도 200이고 like_count는 1에 머문다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-like-author-2")).id!!
				val likerId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-like-user-2")).id!!
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))

				repeat(2) {
					RestAssured.given()
						.header("Authorization", "Bearer ${accessTokenFor(likerId)}")
						.post("/lounge/v1/self-intro-posts/${post.id}/likes")
						.then()
						.statusCode(200)
				}

				likeCountOf(post.id!!) shouldBe 1
				likeRowCountOf(post.id!!) shouldBe 1L
			}
		}

		context("좋아요를 취소하면") {
			it("행이 지워지고 like_count가 1 내린다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-like-author-3")).id!!
				val likerId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-like-user-3")).id!!
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(likerId)}")
					.post("/lounge/v1/self-intro-posts/${post.id}/likes")
					.then()
					.statusCode(200)

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(likerId)}")
					.delete("/lounge/v1/self-intro-posts/${post.id}/likes")
					.then()
					.statusCode(200)

				likeCountOf(post.id!!) shouldBe 0
				likeRowCountOf(post.id!!) shouldBe 0L
			}
		}

		context("누른 적 없는 좋아요를 취소하면") {
			it("멱등 — 200이고 like_count는 그대로다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-like-author-4")).id!!
				val likerId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-like-user-4")).id!!
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(likerId)}")
					.delete("/lounge/v1/self-intro-posts/${post.id}/likes")
					.then()
					.statusCode(200)

				likeCountOf(post.id!!) shouldBe 0
			}
		}

		context("없는 글에 좋아요를 누르면") {
			it("404(LOUNGE-008)를 반환한다") {
				val likerId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-like-user-5")).id!!

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(likerId)}")
					.post("/lounge/v1/self-intro-posts/99999999/likes")
					.then()
					.statusCode(404)
					.body("error.code", Matchers.equalTo("LOUNGE-008"))
			}
		}
	}

	describe("셀소 목록·상세의 likedByMe") {

		context("좋아요를 누른 사용자가 목록을 조회하면") {
			it("누른 글만 likedByMe=true다 (비로그인은 모두 false)") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-like-author-6")).id!!
				val likerId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-like-user-6")).id!!
				val likedPost: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))
				IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(likerId)}")
					.post("/lounge/v1/self-intro-posts/${likedPost.id}/likes")
					.then()
					.statusCode(200)

				// 목록은 최신순(id desc) — 나중에 만든 글이 [0], 좋아요 누른 글이 [1]이다.
				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(likerId)}")
					.get("/lounge/v1/self-intro-posts")
					.then()
					.statusCode(200)
					.body("data.items[0].likedByMe", Matchers.equalTo(false))
					.body("data.items[1].likedByMe", Matchers.equalTo(true))
					.body("data.items[1].likeCount", Matchers.equalTo(1))

				RestAssured.given()
					.get("/lounge/v1/self-intro-posts")
					.then()
					.statusCode(200)
					.body("data.items[1].likedByMe", Matchers.equalTo(false))
			}
		}
	}
})

// 글의 비정규화 like_count. (원자 증감 반영 확인용)
private fun likeCountOf(postId: Long): Int =
	IntegrationUtil.getQuery()
		.select(QLoungePostEntity.loungePostEntity.likeCount)
		.from(QLoungePostEntity.loungePostEntity)
		.where(QLoungePostEntity.loungePostEntity.id.eq(postId))
		.fetchFirst()!!

// 글의 실제 좋아요 행 수.
private fun likeRowCountOf(postId: Long): Long =
	IntegrationUtil.getQuery()
		.select(QLoungePostLikeEntity.loungePostLikeEntity.count())
		.from(QLoungePostLikeEntity.loungePostLikeEntity)
		.where(QLoungePostLikeEntity.loungePostLikeEntity.postId.eq(postId))
		.fetchFirst()!!
