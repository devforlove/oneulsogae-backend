package com.org.oneulsogae.api.mission

import com.org.oneulsogae.common.coin.CoinGetType
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.infra.coin.command.entity.QCoinBalanceEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinHistoryEntity
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.MissionEntityFixture
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.mission.command.entity.QMissionCompletionEntity
import com.org.oneulsogae.infra.mission.command.entity.QMissionEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import io.kotest.matchers.shouldBe

/**
 * `POST /missions/v1/{missionId}/claim` E2E 테스트.
 * 자격 충족 시 코인 적립 + 완료 기록, 재수령 409, 부적격 400, 없는 미션 404를 검증한다.
 */
class ClaimMissionE2ETest : AbstractIntegrationSupport({

	afterTest {
		IntegrationUtil.deleteAll(QMissionCompletionEntity.missionCompletionEntity)
		IntegrationUtil.deleteAll(QMissionEntity.missionEntity)
		IntegrationUtil.deleteAll(QCoinHistoryEntity.coinHistoryEntity)
		IntegrationUtil.deleteAll(QCoinBalanceEntity.coinBalanceEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
	}

	fun completionCount(userId: Long, missionId: Long): Int {
		val c = QMissionCompletionEntity.missionCompletionEntity
		return IntegrationUtil.getQuery().selectFrom(c)
			.where(c.userId.eq(userId), c.missionId.eq(missionId)).fetch().size
	}

	fun balanceOf(userId: Long): Int? {
		val b = QCoinBalanceEntity.coinBalanceEntity
		return IntegrationUtil.getQuery().selectFrom(b).where(b.userId.eq(userId)).fetchOne()?.balance
	}

	fun missionRewardHistoryCount(userId: Long): Int {
		val h = QCoinHistoryEntity.coinHistoryEntity
		return IntegrationUtil.getQuery().selectFrom(h)
			.where(h.userId.eq(userId), h.coinGetType.eq(CoinGetType.MISSION)).fetch().size
	}

	describe("POST /missions/v1/{missionId}/claim") {

		context("자격을 충족한 사용자가 수령하면") {
			it("50코인을 적립하고 완료 기록을 남긴다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "mission-claim-1")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, introduction = "가".repeat(120)))
				val missionId: Long = IntegrationUtil.persist(MissionEntityFixture.create()).id!!

				post("/missions/v1/$missionId/claim") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.rewardedCoin", 50)
					body("data.balance", 50)
				}

				completionCount(userId, missionId) shouldBe 1
				balanceOf(userId) shouldBe 50
				missionRewardHistoryCount(userId) shouldBe 1
			}
		}

		context("이미 수령한 미션을 다시 수령하면") {
			it("409(MISSION-003)이고 재적립되지 않는다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "mission-claim-2")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, introduction = "가".repeat(120)))
				val missionId: Long = IntegrationUtil.persist(MissionEntityFixture.create()).id!!

				post("/missions/v1/$missionId/claim") { bearer(accessTokenFor(userId)) } expect { status(200) }

				post("/missions/v1/$missionId/claim") {
					bearer(accessTokenFor(userId))
				} expect {
					status(409)
					body("error.code", "MISSION-003")
				}

				completionCount(userId, missionId) shouldBe 1
				balanceOf(userId) shouldBe 50
			}
		}

		context("자격 미충족(소개 100자 미만)으로 수령하면") {
			it("400(MISSION-002)이고 적립되지 않는다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "mission-claim-3")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, introduction = "가".repeat(50)))
				val missionId: Long = IntegrationUtil.persist(MissionEntityFixture.create()).id!!

				post("/missions/v1/$missionId/claim") {
					bearer(accessTokenFor(userId))
				} expect {
					status(400)
					body("error.code", "MISSION-002")
				}

				completionCount(userId, missionId) shouldBe 0
				balanceOf(userId) shouldBe null
			}
		}

		context("없는(또는 비활성) 미션을 수령하면") {
			it("404(MISSION-001)를 반환한다") {
				val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "mission-claim-4")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, introduction = "가".repeat(120)))
				val inactiveMissionId: Long = IntegrationUtil.persist(MissionEntityFixture.create(active = false)).id!!

				post("/missions/v1/$inactiveMissionId/claim") {
					bearer(accessTokenFor(userId))
				} expect {
					status(404)
					body("error.code", "MISSION-001")
				}
			}
		}
	}
})
