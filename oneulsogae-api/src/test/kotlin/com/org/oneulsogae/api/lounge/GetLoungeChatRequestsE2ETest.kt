package com.org.oneulsogae.api.lounge

import com.org.oneulsogae.common.chat.ChatRoomMatchType
import com.org.oneulsogae.common.coin.CoinUsageType
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.lounge.LoungeChatRequestStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.chat.command.entity.ChatRoomEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomEntity
import com.org.oneulsogae.infra.fixture.ChatRoomEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.LoungeChatRequestEntityFixture
import com.org.oneulsogae.infra.fixture.LoungePostEntityFixture
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.lounge.command.entity.LoungeChatRequestEntity
import com.org.oneulsogae.infra.lounge.command.entity.LoungePostEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungeChatRequestEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungePostEntity
import io.restassured.RestAssured
import org.hamcrest.Matchers
import java.time.LocalDate
import java.time.Period

/**
 * `GET /lounge/v1/self-intro-posts/{postId}/chat-requests` E2E 테스트.
 * 작성자가 받은 신청을 신청자 프로필·상태·채팅방 id와 함께 최신순으로 받는지, 남의 글은 막히는지 검증한다.
 */
class GetLoungeChatRequestsE2ETest : AbstractIntegrationSupport({

	afterTest {
		IntegrationUtil.deleteAll(QChatRoomEntity.chatRoomEntity)
		IntegrationUtil.deleteAll(QLoungeChatRequestEntity.loungeChatRequestEntity)
		IntegrationUtil.deleteAll(QLoungePostEntity.loungePostEntity)
	}

	describe("GET /lounge/v1/self-intro-posts/{postId}/chat-requests") {

		context("작성자가 자기 글의 신청 목록을 조회하면") {
			it("신청자 프로필·상태를 최신순으로 내려주고 수락된 건에만 chatRoomId가 채워진다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-list-author-1")).id!!
				val olderRequesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-list-user-1")).id!!
				val newerRequesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-list-user-2")).id!!
				val birthday: LocalDate = LocalDate.of(1995, 3, 3)
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(
						userId = olderRequesterId,
						nickname = "먼저신청",
						gender = Gender.MALE,
						birthday = birthday,
					),
				)
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(
						userId = newerRequesterId,
						nickname = "나중신청",
						gender = Gender.MALE,
						birthday = birthday,
					),
				)
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))

				val olderRequest: LoungeChatRequestEntity = IntegrationUtil.persist(
					LoungeChatRequestEntityFixture.create(
						postId = post.id!!,
						requesterUserId = olderRequesterId,
						status = LoungeChatRequestStatus.ACCEPTED,
					),
				)
				IntegrationUtil.persist(
					LoungeChatRequestEntityFixture.create(
						postId = post.id!!,
						requesterUserId = newerRequesterId,
						status = LoungeChatRequestStatus.PENDING,
					),
				)
				val chatRoom: ChatRoomEntity = IntegrationUtil.persist(
					ChatRoomEntityFixture.create(matchType = ChatRoomMatchType.LOUNGE, matchId = olderRequest.id!!),
				)
				val expectedAge: Int = Period.between(birthday, LocalDate.now()).years

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(authorId)}")
					.get("/lounge/v1/self-intro-posts/${post.id}/chat-requests")
					.then()
					.statusCode(200)
					.body("success", Matchers.equalTo(true))
					.body("data.items", Matchers.hasSize<Any>(2))
					// 최신순이라 나중에 신청한 사람이 앞에 온다.
					.body("data.items[0].nickname", Matchers.equalTo("나중신청"))
					.body("data.items[0].status", Matchers.equalTo("PENDING"))
					.body("data.items[0].chatRoomId", Matchers.nullValue())
					.body("data.items[0].age", Matchers.equalTo(expectedAge))
					.body("data.items[0].gender", Matchers.equalTo("MALE"))
					.body("data.items[1].nickname", Matchers.equalTo("먼저신청"))
					.body("data.items[1].status", Matchers.equalTo("ACCEPTED"))
					.body("data.items[1].chatRoomId", Matchers.equalTo(chatRoom.id!!.toInt()))
					// 수락 버튼의 비용 안내값. 신청마다 다르지 않은 전역 정책값(LOUNGE_CHAT_ACCEPT)이라 응답 루트에 한 번만 실린다.
					.body("data.acceptCoinAmount", Matchers.equalTo(CoinUsageType.LOUNGE_CHAT_ACCEPT.coinAmount))
					.body("data.hasNext", Matchers.equalTo(false))
					.body("data.nextCursor", Matchers.nullValue())
			}
		}

		context("남의 글의 신청 목록을 조회하면") {
			it("403(LOUNGE-011)을 반환한다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-list-author-2")).id!!
				val otherId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-list-user-3")).id!!
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(otherId)}")
					.get("/lounge/v1/self-intro-posts/${post.id}/chat-requests")
					.then()
					.statusCode(403)
					.body("error.code", Matchers.equalTo("LOUNGE-011"))
			}
		}

		context("없는 글의 신청 목록을 조회하면") {
			it("404(LOUNGE-008)를 반환한다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-list-user-4")).id!!

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.get("/lounge/v1/self-intro-posts/99999999/chat-requests")
					.then()
					.statusCode(404)
					.body("error.code", Matchers.equalTo("LOUNGE-008"))
			}
		}

		context("한 글에 페이지 크기(20)를 넘는 21건의 신청이 있으면") {
			it("첫 페이지는 20건과 다음 커서를 내려주고, 커서로 나머지 1건을 이어서 받는다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-list-author-3")).id!!
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))
				// (post_id, requester_user_id) 유니크 제약이 있어 신청자는 각각 다른 사용자여야 한다.
				val requestIds: List<Long> = (1..21).map { index: Int ->
					val requesterId: Long = IntegrationUtil.persist(
						UserEntityFixture.create(providerId = "lounge-list-cursor-$index"),
					).id!!
					IntegrationUtil.persist(
						LoungeChatRequestEntityFixture.create(postId = post.id!!, requesterUserId = requesterId),
					).id!!
				}
				val cursor: Long = requestIds[1] // 첫 페이지 마지막(가장 오래된) 항목의 requestId

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(authorId)}")
					.get("/lounge/v1/self-intro-posts/${post.id}/chat-requests")
					.then()
					.statusCode(200)
					.body("data.items", Matchers.hasSize<Any>(20))
					.body("data.hasNext", Matchers.equalTo(true))
					.body("data.nextCursor", Matchers.equalTo(cursor.toInt()))
					// 최신순이라 가장 나중에 신청한 건이 맨 앞, 잘라내기 직전(20번째) 건이 커서와 같다.
					.body("data.items[0].requestId", Matchers.equalTo(requestIds[20].toInt()))
					.body("data.items[19].requestId", Matchers.equalTo(cursor.toInt()))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(authorId)}")
					.queryParam("cursor", cursor)
					.get("/lounge/v1/self-intro-posts/${post.id}/chat-requests")
					.then()
					.statusCode(200)
					.body("data.items", Matchers.hasSize<Any>(1))
					// 남은 가장 오래된 건이며, 첫 페이지 마지막 건(cursor)과 겹치지 않고 바로 이어진다.
					.body("data.items[0].requestId", Matchers.equalTo(requestIds[0].toInt()))
					.body("data.hasNext", Matchers.equalTo(false))
					.body("data.nextCursor", Matchers.nullValue())
			}
		}
	}
})
