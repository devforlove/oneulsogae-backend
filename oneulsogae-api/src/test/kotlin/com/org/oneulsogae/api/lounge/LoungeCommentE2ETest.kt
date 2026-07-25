package com.org.oneulsogae.api.lounge

import com.org.oneulsogae.common.alarm.AlarmType
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.alarm.command.entity.AlarmEntity
import com.org.oneulsogae.infra.alarm.command.entity.QAlarmEntity
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.LoungeCommentEntityFixture
import com.org.oneulsogae.infra.fixture.LoungePostEntityFixture
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.lounge.command.entity.LoungeCommentEntity
import com.org.oneulsogae.infra.lounge.command.entity.LoungePostEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungeCommentEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungePostEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.querydsl.jpa.impl.JPAQueryFactory
import io.kotest.matchers.shouldBe
import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.hamcrest.Matchers
import java.time.LocalDateTime

/**
 * 라운지 댓글 E2E 테스트.
 * 작성(댓글·대댓글·깊이 제한·알람), 조회(중첩·삭제 마스킹·mine·비로그인), 수정·삭제(소유권) 흐름을 검증한다.
 */
class LoungeCommentE2ETest : AbstractIntegrationSupport({

	afterTest {
		IntegrationUtil.deleteAll(QAlarmEntity.alarmEntity)
		IntegrationUtil.deleteAll(QLoungeCommentEntity.loungeCommentEntity)
		IntegrationUtil.deleteAll(QLoungePostEntity.loungePostEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
	}

	describe("POST /lounge/v1/self-intro-posts/{postId}/comments") {

		context("다른 사람의 셀소에 댓글을 달면") {
			it("댓글이 저장되고 글 작성자에게 알람이 쌓인다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-cmt-author-1")).id!!
				val commenterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-cmt-user-1")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = commenterId, nickname = "댓글러"))
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(commenterId)}")
					.contentType(ContentType.JSON)
					.body("""{"content": "첫 댓글입니다"}""")
					.post("/lounge/v1/self-intro-posts/${post.id}/comments")
					.then()
					.statusCode(200)
					.body("success", Matchers.equalTo(true))
					.body("data.commentId", Matchers.notNullValue())

				val saved: LoungeCommentEntity = IntegrationUtil.getQuery()
					.selectFrom(QLoungeCommentEntity.loungeCommentEntity)
					.where(QLoungeCommentEntity.loungeCommentEntity.postId.eq(post.id!!))
					.fetchFirst()!!
				saved.userId shouldBe commenterId
				saved.parentId shouldBe null
				saved.content shouldBe "첫 댓글입니다"

				// 글 작성자에게 "새로운 댓글" 알람.
				val alarms: List<AlarmEntity> = alarmsOf(authorId)
				alarms.size shouldBe 1
				alarms[0].type shouldBe AlarmType.LOUNGE_COMMENT_RECEIVED
				alarms[0].fromUserId shouldBe commenterId
				alarms[0].description shouldBe "댓글러님이 회원님의 셀소에 댓글을 남겼어요."
			}
		}

		context("본인 셀소에 본인이 댓글을 달면") {
			it("댓글은 저장되고 알람은 쌓이지 않는다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-cmt-author-2")).id!!
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(authorId)}")
					.contentType(ContentType.JSON)
					.body("""{"content": "내 글에 내 댓글"}""")
					.post("/lounge/v1/self-intro-posts/${post.id}/comments")
					.then()
					.statusCode(200)

				alarmsOf(authorId).size shouldBe 0
			}
		}

		context("댓글에 답글(대댓글)을 달면") {
			it("parentId가 채워지고 부모 댓글 작성자에게 알람이 쌓인다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-cmt-author-3")).id!!
				val commenterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-cmt-user-3")).id!!
				val replierId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-cmt-user-3r")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = replierId, nickname = "답글러"))
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))
				val parent: LoungeCommentEntity =
					IntegrationUtil.persist(LoungeCommentEntityFixture.create(postId = post.id!!, userId = commenterId))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(replierId)}")
					.contentType(ContentType.JSON)
					.body("""{"content": "대댓글입니다", "parentCommentId": ${parent.id}}""")
					.post("/lounge/v1/self-intro-posts/${post.id}/comments")
					.then()
					.statusCode(200)

				val savedReply: LoungeCommentEntity = IntegrationUtil.getQuery()
					.selectFrom(QLoungeCommentEntity.loungeCommentEntity)
					.where(QLoungeCommentEntity.loungeCommentEntity.parentId.eq(parent.id!!))
					.fetchFirst()!!
				savedReply.userId shouldBe replierId

				// 부모 댓글 작성자에게 "새로운 답글" 알람. (글 작성자에게는 쌓이지 않는다)
				val alarms: List<AlarmEntity> = alarmsOf(commenterId)
				alarms.size shouldBe 1
				alarms[0].type shouldBe AlarmType.LOUNGE_COMMENT_REPLY_RECEIVED
				alarms[0].description shouldBe "답글러님이 회원님의 댓글에 답글을 남겼어요."
				alarmsOf(authorId).size shouldBe 0
			}
		}

		context("대댓글에 다시 답글을 달면") {
			it("400(LOUNGE-019)을 반환한다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-cmt-author-4")).id!!
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))
				val parent: LoungeCommentEntity =
					IntegrationUtil.persist(LoungeCommentEntityFixture.create(postId = post.id!!, userId = authorId))
				val reply: LoungeCommentEntity = IntegrationUtil.persist(
					LoungeCommentEntityFixture.create(postId = post.id!!, userId = authorId, parentId = parent.id),
				)

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(authorId)}")
					.contentType(ContentType.JSON)
					.body("""{"content": "답글의 답글", "parentCommentId": ${reply.id}}""")
					.post("/lounge/v1/self-intro-posts/${post.id}/comments")
					.then()
					.statusCode(400)
					.body("error.code", Matchers.equalTo("LOUNGE-019"))
			}
		}

		context("내용 없이 댓글을 달면") {
			it("400(LOUNGE-017)을 반환한다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-cmt-author-5")).id!!
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(authorId)}")
					.contentType(ContentType.JSON)
					.body("""{"content": "  "}""")
					.post("/lounge/v1/self-intro-posts/${post.id}/comments")
					.then()
					.statusCode(400)
					.body("error.code", Matchers.equalTo("LOUNGE-017"))
			}
		}

		context("없는 글에 댓글을 달면") {
			it("404(LOUNGE-008)를 반환한다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-cmt-user-6")).id!!

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.contentType(ContentType.JSON)
					.body("""{"content": "댓글"}""")
					.post("/lounge/v1/self-intro-posts/99999999/comments")
					.then()
					.statusCode(404)
					.body("error.code", Matchers.equalTo("LOUNGE-008"))
			}
		}
	}

	describe("GET /lounge/v1/self-intro-posts/{postId}/comments") {

		context("댓글과 대댓글이 있는 글을 조회하면") {
			it("root는 오래된 순으로, 대댓글은 각 root에 중첩돼 내려간다 (비로그인 허용)") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-cmt-author-7")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = authorId, nickname = "글쓴이", gender = Gender.FEMALE))
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))
				val first: LoungeCommentEntity = IntegrationUtil.persist(
					LoungeCommentEntityFixture.create(postId = post.id!!, userId = authorId, content = "첫 댓글"),
				)
				IntegrationUtil.persist(
					LoungeCommentEntityFixture.create(postId = post.id!!, userId = authorId, content = "둘째 댓글"),
				)
				IntegrationUtil.persist(
					LoungeCommentEntityFixture.create(postId = post.id!!, userId = authorId, parentId = first.id, content = "첫 댓글의 답글"),
				)

				// 비로그인 조회 — mine은 모두 false다.
				RestAssured.given()
					.get("/lounge/v1/self-intro-posts/${post.id}/comments")
					.then()
					.statusCode(200)
					.body("data.comments.size()", Matchers.equalTo(2))
					.body("data.comments[0].content", Matchers.equalTo("첫 댓글"))
					.body("data.comments[0].authorNickname", Matchers.equalTo("글쓴이"))
					// 아바타가 성별+아바타 번호 조합으로 그려지므로 작성자 성별이 함께 내려간다.
					.body("data.comments[0].authorGender", Matchers.equalTo("FEMALE"))
					.body("data.comments[0].mine", Matchers.equalTo(false))
					.body("data.comments[0].replies.size()", Matchers.equalTo(1))
					.body("data.comments[0].replies[0].content", Matchers.equalTo("첫 댓글의 답글"))
					.body("data.comments[1].content", Matchers.equalTo("둘째 댓글"))
					.body("data.comments[1].replies.size()", Matchers.equalTo(0))
					.body("data.hasNext", Matchers.equalTo(false))

				// 로그인 조회 — 본인 댓글은 mine=true다.
				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(authorId)}")
					.get("/lounge/v1/self-intro-posts/${post.id}/comments")
					.then()
					.statusCode(200)
					.body("data.comments[0].mine", Matchers.equalTo(true))
			}
		}

		context("삭제된 댓글을 조회하면") {
			it("대댓글이 남은 root는 deleted=true·content=null로, 대댓글 없는 삭제 root와 삭제 대댓글은 빠진다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-cmt-author-8")).id!!
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))
				// 삭제됐지만 살아있는 대댓글이 남은 root — "삭제된 댓글"로 노출된다.
				val deletedWithReply: LoungeCommentEntity = IntegrationUtil.persist(
					LoungeCommentEntityFixture.create(postId = post.id!!, userId = authorId, content = "삭제될 댓글"),
				)
				IntegrationUtil.persist(
					LoungeCommentEntityFixture.create(postId = post.id!!, userId = authorId, parentId = deletedWithReply.id, content = "남는 답글"),
				)
				// 대댓글 없이 삭제된 root — 목록에서 빠진다.
				val deletedAlone: LoungeCommentEntity = IntegrationUtil.persist(
					LoungeCommentEntityFixture.create(postId = post.id!!, userId = authorId, content = "혼자 삭제될 댓글"),
				)
				// 삭제된 대댓글 — 빠진다.
				val deletedReply: LoungeCommentEntity = IntegrationUtil.persist(
					LoungeCommentEntityFixture.create(postId = post.id!!, userId = authorId, parentId = deletedWithReply.id, content = "삭제될 답글"),
				)
				softDelete(deletedWithReply.id!!)
				softDelete(deletedAlone.id!!)
				softDelete(deletedReply.id!!)

				RestAssured.given()
					.get("/lounge/v1/self-intro-posts/${post.id}/comments")
					.then()
					.statusCode(200)
					.body("data.comments.size()", Matchers.equalTo(1))
					.body("data.comments[0].deleted", Matchers.equalTo(true))
					.body("data.comments[0].content", Matchers.nullValue())
					.body("data.comments[0].replies.size()", Matchers.equalTo(1))
					.body("data.comments[0].replies[0].content", Matchers.equalTo("남는 답글"))
			}
		}
	}

	describe("PATCH /lounge/v1/comments/{commentId}") {

		context("작성자 본인이 수정하면") {
			it("내용이 바뀐다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-cmt-user-9")).id!!
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = userId))
				val comment: LoungeCommentEntity =
					IntegrationUtil.persist(LoungeCommentEntityFixture.create(postId = post.id!!, userId = userId, content = "원래 내용"))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.contentType(ContentType.JSON)
					.body("""{"content": "수정한 내용"}""")
					.patch("/lounge/v1/comments/${comment.id}")
					.then()
					.statusCode(200)

				val updated: LoungeCommentEntity = IntegrationUtil.getQuery()
					.selectFrom(QLoungeCommentEntity.loungeCommentEntity)
					.where(QLoungeCommentEntity.loungeCommentEntity.id.eq(comment.id!!))
					.fetchFirst()!!
				updated.content shouldBe "수정한 내용"
			}
		}

		context("다른 사람의 댓글을 수정하면") {
			it("403(LOUNGE-018)을 반환한다") {
				val ownerId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-cmt-user-10")).id!!
				val otherId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-cmt-user-10x")).id!!
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = ownerId))
				val comment: LoungeCommentEntity =
					IntegrationUtil.persist(LoungeCommentEntityFixture.create(postId = post.id!!, userId = ownerId))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(otherId)}")
					.contentType(ContentType.JSON)
					.body("""{"content": "남의 댓글 수정"}""")
					.patch("/lounge/v1/comments/${comment.id}")
					.then()
					.statusCode(403)
					.body("error.code", Matchers.equalTo("LOUNGE-018"))
			}
		}

		context("삭제된 댓글을 수정하면") {
			it("404(LOUNGE-016)를 반환한다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-cmt-user-11")).id!!
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = userId))
				val comment: LoungeCommentEntity =
					IntegrationUtil.persist(LoungeCommentEntityFixture.create(postId = post.id!!, userId = userId))
				softDelete(comment.id!!)

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.contentType(ContentType.JSON)
					.body("""{"content": "수정 시도"}""")
					.patch("/lounge/v1/comments/${comment.id}")
					.then()
					.statusCode(404)
					.body("error.code", Matchers.equalTo("LOUNGE-016"))
			}
		}
	}

	describe("DELETE /lounge/v1/comments/{commentId}") {

		context("작성자 본인이 삭제하면") {
			it("soft delete된다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-cmt-user-12")).id!!
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = userId))
				val comment: LoungeCommentEntity =
					IntegrationUtil.persist(LoungeCommentEntityFixture.create(postId = post.id!!, userId = userId))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.delete("/lounge/v1/comments/${comment.id}")
					.then()
					.statusCode(200)

				val deleted: LoungeCommentEntity = IntegrationUtil.getQuery()
					.selectFrom(QLoungeCommentEntity.loungeCommentEntity)
					.where(QLoungeCommentEntity.loungeCommentEntity.id.eq(comment.id!!))
					.fetchFirst()!!
				deleted.isDeleted shouldBe true
			}
		}

		context("다른 사람의 댓글을 삭제하면") {
			it("403(LOUNGE-018)을 반환한다") {
				val ownerId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-cmt-user-13")).id!!
				val otherId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-cmt-user-13x")).id!!
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = ownerId))
				val comment: LoungeCommentEntity =
					IntegrationUtil.persist(LoungeCommentEntityFixture.create(postId = post.id!!, userId = ownerId))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(otherId)}")
					.delete("/lounge/v1/comments/${comment.id}")
					.then()
					.statusCode(403)
					.body("error.code", Matchers.equalTo("LOUNGE-018"))
			}
		}
	}
})

// 해당 사용자의 알람 목록. (알람 저장 확인용 — LoungeChatRequestAlarmE2ETest와 같은 형태)
private fun alarmsOf(userId: Long): List<AlarmEntity> {
	val alarm: QAlarmEntity = QAlarmEntity.alarmEntity
	return IntegrationUtil.getQuery()
		.selectFrom(alarm)
		.where(alarm.userId.eq(userId))
		.fetch()
}

// 댓글을 soft delete 상태로 만든다. (삭제 노출 규칙 픽스처용)
private fun softDelete(commentId: Long) {
	IntegrationUtil.update { queryFactory: JPAQueryFactory ->
		queryFactory
			.update(QLoungeCommentEntity.loungeCommentEntity)
			.set(QLoungeCommentEntity.loungeCommentEntity.deletedAt, LocalDateTime.now())
			.where(QLoungeCommentEntity.loungeCommentEntity.id.eq(commentId))
			.execute()
	}
}
