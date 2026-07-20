package com.org.meeple.api.lounge

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.lounge.LoungePostType
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.lounge.command.entity.LoungePostEntity
import com.org.meeple.infra.lounge.command.entity.LoungePostImageEntity
import com.org.meeple.infra.lounge.command.entity.QLoungePostEntity
import com.org.meeple.infra.lounge.command.entity.QLoungePostImageEntity
import com.org.meeple.infra.lounge.command.entity.QSelfIntroPostEntity
import com.org.meeple.infra.lounge.command.entity.SelfIntroPostEntity
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.restassured.RestAssured
import io.restassured.specification.RequestSpecification
import org.hamcrest.Matchers

/**
 * `POST /lounge/v1/self-intro-posts` E2E 테스트. (멀티파트 업로드)
 * 사진과 본문을 등록하면 lounge_posts·self_intro_posts·lounge_post_images에 저장되는지,
 * 사진 누락·잘못된 형식·본문 누락이 막히는지, 최근 24시간 1건 제한이 동작하는지 검증한다.
 * (실제 S3 업로드는 [com.org.meeple.common.config.TestFileStorageConfig]의 페이크로 대체)
 */
class SelfIntroPostE2ETest : AbstractIntegrationSupport({

	fun persistUser(providerId: String): Long =
		IntegrationUtil.persist(UserEntityFixture.create(providerId = providerId)).id!!

	fun latestPostOf(userId: Long): LoungePostEntity? {
		val post: QLoungePostEntity = QLoungePostEntity.loungePostEntity
		return IntegrationUtil.getQuery().selectFrom(post).where(post.userId.eq(userId)).orderBy(post.id.desc()).fetchFirst()
	}

	fun selfIntroOf(postId: Long): SelfIntroPostEntity? {
		val selfIntro: QSelfIntroPostEntity = QSelfIntroPostEntity.selfIntroPostEntity
		return IntegrationUtil.getQuery().selectFrom(selfIntro).where(selfIntro.postId.eq(postId)).fetchFirst()
	}

	fun imagesOf(postId: Long): List<LoungePostImageEntity> {
		val image: QLoungePostImageEntity = QLoungePostImageEntity.loungePostImageEntity
		return IntegrationUtil.getQuery().selectFrom(image)
			.where(image.postId.eq(postId))
			.orderBy(image.displayOrder.asc())
			.fetch()
	}

	/** 본문 7개 항목을 모두 채운 요청. 사진은 호출부가 덧붙인다. */
	fun requestWithContent(userId: Long): RequestSpecification =
		RestAssured.given()
			.header("Authorization", "Bearer ${accessTokenFor(userId)}")
			.multiPart("longDistance", "장거리 가능해요", "text/plain;charset=UTF-8")
			.multiPart("desiredAge", "28~34세", "text/plain;charset=UTF-8")
			.multiPart("mbti", "ENFP", "text/plain;charset=UTF-8")
			.multiPart("marriageThought", "3년 안에 하고 싶어요", "text/plain;charset=UTF-8")
			.multiPart("preferredPartner", "대화가 잘 통하는 사람", "text/plain;charset=UTF-8")
			.multiPart("charmPoint", "잘 웃어요", "text/plain;charset=UTF-8")
			.multiPart("freeWord", "편하게 연락 주세요", "text/plain;charset=UTF-8")

	describe("POST /lounge/v1/self-intro-posts") {

		context("사진 2장과 본문을 등록하면") {
			it("lounge_posts·self_intro_posts·lounge_post_images에 저장하고 postId를 반환한다") {
				val userId: Long = persistUser("self-intro-1")

				requestWithContent(userId)
					.multiPart("photos", "first.jpg", "fake-first-bytes".toByteArray(), "image/jpeg")
					.multiPart("photos", "second.png", "fake-second-bytes".toByteArray(), "image/png")
					.post("/lounge/v1/self-intro-posts")
					.then()
					.statusCode(200)
					.body("success", Matchers.equalTo(true))
					.body("data.postId", Matchers.notNullValue())

				val post: LoungePostEntity = latestPostOf(userId)!!
				post.type shouldBe LoungePostType.SELF_INTRO
				post.likeCount shouldBe 0

				val selfIntro: SelfIntroPostEntity = selfIntroOf(post.id!!)!!
				selfIntro.mbti shouldBe "ENFP"
				selfIntro.desiredAge shouldBe "28~34세"
				selfIntro.freeWord shouldBe "편하게 연락 주세요"

				val images: List<LoungePostImageEntity> = imagesOf(post.id!!)
				images.size shouldBe 2
				images[0].displayOrder shouldBe 0
				images[0].imageKey shouldStartWith "lounge-posts/$userId/"
				images[1].displayOrder shouldBe 1
				images[1].imageKey shouldStartWith "lounge-posts/$userId/"
			}
		}

		context("사진 없이 본문만 등록하면") {
			it("400(LOUNGE-001)을 반환하고 저장하지 않는다") {
				val userId: Long = persistUser("self-intro-2")

				requestWithContent(userId)
					.post("/lounge/v1/self-intro-posts")
					.then()
					.statusCode(400)
					.body("success", Matchers.equalTo(false))
					.body("error.code", Matchers.equalTo("LOUNGE-001"))

				latestPostOf(userId) shouldBe null
			}
		}

		context("허용하지 않는 형식(gif)의 사진을 등록하면") {
			it("400(LOUNGE-004)을 반환하고 저장하지 않는다") {
				val userId: Long = persistUser("self-intro-3")

				requestWithContent(userId)
					.multiPart("photos", "photo.gif", "gif-bytes".toByteArray(), "image/gif")
					.post("/lounge/v1/self-intro-posts")
					.then()
					.statusCode(400)
					.body("error.code", Matchers.equalTo("LOUNGE-004"))

				latestPostOf(userId) shouldBe null
			}
		}

		context("본문 항목을 비워 등록하면") {
			it("400(LOUNGE-006)을 반환하고 저장하지 않는다") {
				val userId: Long = persistUser("self-intro-4")

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.multiPart("longDistance", "장거리 가능해요", "text/plain;charset=UTF-8")
					.multiPart("desiredAge", "28~34세", "text/plain;charset=UTF-8")
					.multiPart("mbti", "ENFP", "text/plain;charset=UTF-8")
					.multiPart("marriageThought", "3년 안에 하고 싶어요", "text/plain;charset=UTF-8")
					.multiPart("preferredPartner", "대화가 잘 통하는 사람", "text/plain;charset=UTF-8")
					.multiPart("charmPoint", "잘 웃어요", "text/plain;charset=UTF-8")
					.multiPart("photos", "first.jpg", "fake-first-bytes".toByteArray(), "image/jpeg")
					.post("/lounge/v1/self-intro-posts")
					.then()
					.statusCode(400)
					.body("error.code", Matchers.equalTo("LOUNGE-006"))

				latestPostOf(userId) shouldBe null
			}
		}

		context("최근 24시간 안에 이미 등록한 유저가 다시 등록하면") {
			it("429(LOUNGE-007)를 반환하고 두 번째 글은 저장하지 않는다") {
				val userId: Long = persistUser("self-intro-5")

				requestWithContent(userId)
					.multiPart("photos", "first.jpg", "fake-first-bytes".toByteArray(), "image/jpeg")
					.post("/lounge/v1/self-intro-posts")
					.then()
					.statusCode(200)

				val firstPostId: Long = latestPostOf(userId)!!.id!!

				requestWithContent(userId)
					.multiPart("photos", "second.jpg", "fake-second-bytes".toByteArray(), "image/jpeg")
					.post("/lounge/v1/self-intro-posts")
					.then()
					.statusCode(429)
					.body("error.code", Matchers.equalTo("LOUNGE-007"))

				latestPostOf(userId)!!.id shouldBe firstPostId
			}
		}
	}
})
