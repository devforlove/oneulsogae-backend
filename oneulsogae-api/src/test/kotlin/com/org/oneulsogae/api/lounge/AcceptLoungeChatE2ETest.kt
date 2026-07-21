package com.org.oneulsogae.api.lounge

import com.org.oneulsogae.common.chat.ChatRoomMatchType
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.lounge.LoungeChatRequestStatus
import com.org.oneulsogae.infra.chat.command.entity.ChatRoomEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomEntity
import com.org.oneulsogae.infra.chat.command.entity.QChatRoomMemberEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinBalanceEntity
import com.org.oneulsogae.infra.fixture.CoinBalanceEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.LoungeChatRequestEntityFixture
import com.org.oneulsogae.infra.fixture.LoungePostEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.lounge.command.entity.LoungeChatRequestEntity
import com.org.oneulsogae.infra.lounge.command.entity.LoungePostEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungeChatRequestEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungePostEntity
import io.kotest.matchers.shouldBe
import io.restassured.RestAssured
import org.hamcrest.Matchers

/**
 * `POST /lounge/v1/chat-requests/{requestId}/accept` E2E 테스트.
 * 수락으로 LOUNGE 채팅방과 참가자 2명이 생기고 코인 32가 차감되는지, 여러 명 수락·중복 수락·권한·잔액 부족을 검증한다.
 */
class AcceptLoungeChatE2ETest : AbstractIntegrationSupport({

	afterTest {
		IntegrationUtil.deleteAll(QChatRoomMemberEntity.chatRoomMemberEntity)
		IntegrationUtil.deleteAll(QChatRoomEntity.chatRoomEntity)
		IntegrationUtil.deleteAll(QLoungeChatRequestEntity.loungeChatRequestEntity)
		IntegrationUtil.deleteAll(QLoungePostEntity.loungePostEntity)
		IntegrationUtil.deleteAll(QCoinBalanceEntity.coinBalanceEntity)
	}

	describe("POST /lounge/v1/chat-requests/{requestId}/accept") {

		context("작성자가 받은 신청을 수락하면") {
			it("LOUNGE 채팅방과 참가자 2명이 생기고 코인 32가 차감된다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-acc-author-1")).id!!
				val requesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-acc-user-1")).id!!
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = authorId, balance = 100))
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))
				val request: LoungeChatRequestEntity = IntegrationUtil.persist(
					LoungeChatRequestEntityFixture.create(postId = post.id!!, requesterUserId = requesterId),
				)

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(authorId)}")
					.post("/lounge/v1/chat-requests/${request.id}/accept")
					.then()
					.statusCode(200)
					.body("success", Matchers.equalTo(true))
					.body("data.chatRoomId", Matchers.notNullValue())

				val chatRoom: ChatRoomEntity = IntegrationUtil.getQuery()
					.selectFrom(QChatRoomEntity.chatRoomEntity)
					.where(
						QChatRoomEntity.chatRoomEntity.matchType.eq(ChatRoomMatchType.LOUNGE),
						QChatRoomEntity.chatRoomEntity.matchId.eq(request.id!!),
					)
					.fetchFirst()!!

				val memberUserIds: List<Long> = IntegrationUtil.getQuery()
					.select(QChatRoomMemberEntity.chatRoomMemberEntity.userId)
					.from(QChatRoomMemberEntity.chatRoomMemberEntity)
					.where(QChatRoomMemberEntity.chatRoomMemberEntity.chatRoomId.eq(chatRoom.id!!))
					.fetch()
				memberUserIds.toSet() shouldBe setOf(authorId, requesterId)

				val status: LoungeChatRequestStatus = IntegrationUtil.getQuery()
					.select(QLoungeChatRequestEntity.loungeChatRequestEntity.status)
					.from(QLoungeChatRequestEntity.loungeChatRequestEntity)
					.where(QLoungeChatRequestEntity.loungeChatRequestEntity.id.eq(request.id!!))
					.fetchFirst()!!
				status shouldBe LoungeChatRequestStatus.ACCEPTED

				val balance: Int = IntegrationUtil.getQuery()
					.select(QCoinBalanceEntity.coinBalanceEntity.balance)
					.from(QCoinBalanceEntity.coinBalanceEntity)
					.where(QCoinBalanceEntity.coinBalanceEntity.userId.eq(authorId))
					.fetchFirst()!!
				balance shouldBe 68
			}
		}

		context("같은 글에 온 신청 두 건을 모두 수락하면") {
			it("채팅방이 두 개 생긴다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-acc-author-2")).id!!
				val firstRequesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-acc-user-2")).id!!
				val secondRequesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-acc-user-3")).id!!
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = authorId, balance = 100))
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))
				val firstRequest: LoungeChatRequestEntity = IntegrationUtil.persist(
					LoungeChatRequestEntityFixture.create(postId = post.id!!, requesterUserId = firstRequesterId),
				)
				val secondRequest: LoungeChatRequestEntity = IntegrationUtil.persist(
					LoungeChatRequestEntityFixture.create(postId = post.id!!, requesterUserId = secondRequesterId),
				)

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(authorId)}")
					.post("/lounge/v1/chat-requests/${firstRequest.id}/accept")
					.then()
					.statusCode(200)

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(authorId)}")
					.post("/lounge/v1/chat-requests/${secondRequest.id}/accept")
					.then()
					.statusCode(200)

				val roomCount: Int = IntegrationUtil.getQuery()
					.selectFrom(QChatRoomEntity.chatRoomEntity)
					.where(QChatRoomEntity.chatRoomEntity.matchType.eq(ChatRoomMatchType.LOUNGE))
					.fetch()
					.size
				roomCount shouldBe 2

				val balance: Int = IntegrationUtil.getQuery()
					.select(QCoinBalanceEntity.coinBalanceEntity.balance)
					.from(QCoinBalanceEntity.coinBalanceEntity)
					.where(QCoinBalanceEntity.coinBalanceEntity.userId.eq(authorId))
					.fetchFirst()!!
				balance shouldBe 36
			}
		}

		context("이미 수락한 신청을 다시 수락하면") {
			it("409(LOUNGE-013)이고 코인은 한 번만 차감된다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-acc-author-3")).id!!
				val requesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-acc-user-4")).id!!
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = authorId, balance = 100))
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))
				val request: LoungeChatRequestEntity = IntegrationUtil.persist(
					LoungeChatRequestEntityFixture.create(postId = post.id!!, requesterUserId = requesterId),
				)

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(authorId)}")
					.post("/lounge/v1/chat-requests/${request.id}/accept")
					.then()
					.statusCode(200)

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(authorId)}")
					.post("/lounge/v1/chat-requests/${request.id}/accept")
					.then()
					.statusCode(409)
					.body("error.code", Matchers.equalTo("LOUNGE-013"))

				val balance: Int = IntegrationUtil.getQuery()
					.select(QCoinBalanceEntity.coinBalanceEntity.balance)
					.from(QCoinBalanceEntity.coinBalanceEntity)
					.where(QCoinBalanceEntity.coinBalanceEntity.userId.eq(authorId))
					.fetchFirst()!!
				balance shouldBe 68
			}
		}

		context("작성자가 아닌 사람이 수락하면") {
			it("403(LOUNGE-011)을 반환한다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-acc-author-4")).id!!
				val requesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-acc-user-5")).id!!
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = requesterId, balance = 100))
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))
				val request: LoungeChatRequestEntity = IntegrationUtil.persist(
					LoungeChatRequestEntityFixture.create(postId = post.id!!, requesterUserId = requesterId),
				)

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(requesterId)}")
					.post("/lounge/v1/chat-requests/${request.id}/accept")
					.then()
					.statusCode(403)
					.body("error.code", Matchers.equalTo("LOUNGE-011"))
			}
		}

		context("코인이 부족하면") {
			it("채팅방이 생기지 않고 신청 상태도 그대로다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-acc-author-5")).id!!
				val requesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-acc-user-6")).id!!
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = authorId, balance = 5))
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))
				val request: LoungeChatRequestEntity = IntegrationUtil.persist(
					LoungeChatRequestEntityFixture.create(postId = post.id!!, requesterUserId = requesterId),
				)

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(authorId)}")
					.post("/lounge/v1/chat-requests/${request.id}/accept")
					.then()
					.statusCode(400)
					.body("error.code", Matchers.equalTo("COIN-001"))

				val roomCount: Int = IntegrationUtil.getQuery()
					.selectFrom(QChatRoomEntity.chatRoomEntity)
					.where(QChatRoomEntity.chatRoomEntity.matchId.eq(request.id!!))
					.fetch()
					.size
				roomCount shouldBe 0

				val status: LoungeChatRequestStatus = IntegrationUtil.getQuery()
					.select(QLoungeChatRequestEntity.loungeChatRequestEntity.status)
					.from(QLoungeChatRequestEntity.loungeChatRequestEntity)
					.where(QLoungeChatRequestEntity.loungeChatRequestEntity.id.eq(request.id!!))
					.fetchFirst()!!
				status shouldBe LoungeChatRequestStatus.PENDING
			}
		}

		context("없는 신청을 수락하면") {
			it("404(LOUNGE-012)를 반환한다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-acc-user-7")).id!!
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = userId, balance = 100))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(userId)}")
					.post("/lounge/v1/chat-requests/99999999/accept")
					.then()
					.statusCode(404)
					.body("error.code", Matchers.equalTo("LOUNGE-012"))
			}
		}
	}
})
