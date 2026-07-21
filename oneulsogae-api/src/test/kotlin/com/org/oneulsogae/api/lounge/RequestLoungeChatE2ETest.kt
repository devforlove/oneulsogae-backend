package com.org.oneulsogae.api.lounge

import com.org.oneulsogae.common.coin.CoinUsageType
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.common.lounge.LoungeChatRequestStatus
import com.org.oneulsogae.infra.coin.command.entity.CoinHistoryEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinBalanceEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinHistoryEntity
import com.org.oneulsogae.infra.fixture.CoinBalanceEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.LoungePostEntityFixture
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.lounge.command.entity.LoungeChatRequestEntity
import com.org.oneulsogae.infra.lounge.command.entity.LoungePostEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungeChatRequestEntity
import com.org.oneulsogae.infra.lounge.command.entity.QLoungePostEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import io.kotest.matchers.shouldBe
import io.restassured.RestAssured
import org.hamcrest.Matchers

/**
 * `POST /lounge/v1/self-intro-posts/{postId}/chat-requests` E2E 테스트.
 * 신청 행 생성과 코인 32 차감, 본인 글·중복 신청·잔액 부족·없는 글 차단을 검증한다.
 */
class RequestLoungeChatE2ETest : AbstractIntegrationSupport({

	afterTest {
		IntegrationUtil.deleteAll(QLoungeChatRequestEntity.loungeChatRequestEntity)
		IntegrationUtil.deleteAll(QLoungePostEntity.loungePostEntity)
		IntegrationUtil.deleteAll(QCoinBalanceEntity.coinBalanceEntity)
		IntegrationUtil.deleteAll(QCoinHistoryEntity.coinHistoryEntity)
	}

	describe("POST /lounge/v1/self-intro-posts/{postId}/chat-requests") {

		context("다른 사람의 셀소에 코인을 갖고 신청하면") {
			it("PENDING 신청이 생성되고 코인 32가 차감된다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-req-author-1")).id!!
				val requesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-req-user-1")).id!!
				// 이성에게만 신청할 수 있으므로 두 사람의 성별을 서로 다르게 둔다.
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = authorId, gender = Gender.FEMALE))
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = requesterId, gender = Gender.MALE))
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = requesterId, balance = 100))
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(requesterId)}")
					.post("/lounge/v1/self-intro-posts/${post.id}/chat-requests")
					.then()
					.statusCode(200)
					.body("success", Matchers.equalTo(true))
					.body("data.requestId", Matchers.notNullValue())

				val saved: LoungeChatRequestEntity = IntegrationUtil.getQuery()
					.selectFrom(QLoungeChatRequestEntity.loungeChatRequestEntity)
					.where(QLoungeChatRequestEntity.loungeChatRequestEntity.postId.eq(post.id!!))
					.fetchFirst()!!
				saved.requesterUserId shouldBe requesterId
				saved.status shouldBe LoungeChatRequestStatus.PENDING

				val balance: Int = IntegrationUtil.getQuery()
					.select(QCoinBalanceEntity.coinBalanceEntity.balance)
					.from(QCoinBalanceEntity.coinBalanceEntity)
					.where(QCoinBalanceEntity.coinBalanceEntity.userId.eq(requesterId))
					.fetchFirst()!!
				balance shouldBe 68

				val histories: List<CoinHistoryEntity> = IntegrationUtil.getQuery()
					.selectFrom(QCoinHistoryEntity.coinHistoryEntity)
					.where(QCoinHistoryEntity.coinHistoryEntity.userId.eq(requesterId))
					.fetch()
				histories.size shouldBe 1
				histories[0].amount shouldBe -32
				histories[0].coinUsageType shouldBe CoinUsageType.LOUNGE_CHAT_INIT
			}
		}

		context("본인이 쓴 셀소에 신청하면") {
			it("400(LOUNGE-009)을 반환한다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-req-author-2")).id!!
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = authorId, balance = 100))
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(authorId)}")
					.post("/lounge/v1/self-intro-posts/${post.id}/chat-requests")
					.then()
					.statusCode(400)
					.body("error.code", Matchers.equalTo("LOUNGE-009"))
			}
		}

		context("같은 글에 두 번 신청하면") {
			it("두 번째는 409(LOUNGE-010)이고 코인은 한 번만 차감된다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-req-author-3")).id!!
				val requesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-req-user-3")).id!!
				// 이성에게만 신청할 수 있으므로 두 사람의 성별을 서로 다르게 둔다.
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = authorId, gender = Gender.FEMALE))
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = requesterId, gender = Gender.MALE))
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = requesterId, balance = 100))
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(requesterId)}")
					.post("/lounge/v1/self-intro-posts/${post.id}/chat-requests")
					.then()
					.statusCode(200)

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(requesterId)}")
					.post("/lounge/v1/self-intro-posts/${post.id}/chat-requests")
					.then()
					.statusCode(409)
					.body("error.code", Matchers.equalTo("LOUNGE-010"))

				val balance: Int = IntegrationUtil.getQuery()
					.select(QCoinBalanceEntity.coinBalanceEntity.balance)
					.from(QCoinBalanceEntity.coinBalanceEntity)
					.where(QCoinBalanceEntity.coinBalanceEntity.userId.eq(requesterId))
					.fetchFirst()!!
				balance shouldBe 68
			}
		}

		context("코인이 부족하면") {
			it("신청 행이 남지 않고 실패한다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-req-author-4")).id!!
				val requesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-req-user-4")).id!!
				// 이성에게만 신청할 수 있으므로 두 사람의 성별을 서로 다르게 둔다.
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = authorId, gender = Gender.FEMALE))
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = requesterId, gender = Gender.MALE))
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = requesterId, balance = 5))
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(requesterId)}")
					.post("/lounge/v1/self-intro-posts/${post.id}/chat-requests")
					.then()
					.statusCode(400)
					.body("error.code", Matchers.equalTo("COIN-001"))

				val count: Long = IntegrationUtil.getQuery()
					.selectFrom(QLoungeChatRequestEntity.loungeChatRequestEntity)
					.where(QLoungeChatRequestEntity.loungeChatRequestEntity.postId.eq(post.id!!))
					.fetch()
					.size
					.toLong()
				count shouldBe 0L
			}
		}

		context("성별이 같은 상대의 셀소에 신청하면") {
			it("400(LOUNGE-014)이고 코인이 차감되지 않는다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-req-author-6")).id!!
				val requesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-req-user-6")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = authorId, gender = Gender.MALE))
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = requesterId, gender = Gender.MALE))
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = requesterId, balance = 100))
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(requesterId)}")
					.post("/lounge/v1/self-intro-posts/${post.id}/chat-requests")
					.then()
					.statusCode(400)
					.body("error.code", Matchers.equalTo("LOUNGE-014"))

				val balance: Int = IntegrationUtil.getQuery()
					.select(QCoinBalanceEntity.coinBalanceEntity.balance)
					.from(QCoinBalanceEntity.coinBalanceEntity)
					.where(QCoinBalanceEntity.coinBalanceEntity.userId.eq(requesterId))
					.fetchFirst()!!
				balance shouldBe 100
			}
		}

		context("프로필이 없어 성별을 확인할 수 없으면") {
			it("400(LOUNGE-014)으로 막는다") {
				val authorId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-req-author-7")).id!!
				val requesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-req-user-7")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = requesterId, gender = Gender.MALE))
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = requesterId, balance = 100))
				// 작성자 프로필(성별)이 없다.
				val post: LoungePostEntity = IntegrationUtil.persist(LoungePostEntityFixture.create(userId = authorId))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(requesterId)}")
					.post("/lounge/v1/self-intro-posts/${post.id}/chat-requests")
					.then()
					.statusCode(400)
					.body("error.code", Matchers.equalTo("LOUNGE-014"))
			}
		}

		context("없는 글에 신청하면") {
			it("404(LOUNGE-008)를 반환한다") {
				val requesterId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "lounge-req-user-5")).id!!
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = requesterId, balance = 100))

				RestAssured.given()
					.header("Authorization", "Bearer ${accessTokenFor(requesterId)}")
					.post("/lounge/v1/self-intro-posts/99999999/chat-requests")
					.then()
					.statusCode(404)
					.body("error.code", Matchers.equalTo("LOUNGE-008"))
			}
		}
	}
})
