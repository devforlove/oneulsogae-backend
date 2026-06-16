package com.org.meeple.api.coin

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.infra.coin.command.entity.QCoinBalanceEntity
import com.org.meeple.infra.fixture.CoinBalanceEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import io.kotest.matchers.shouldBe

/**
 * `GET /coins/v1/balance` E2E 테스트.
 *
 * 잔액 행이 없는(적립 이력이 없는) 사용자가 조회하면 행을 만들지 않고 0을 반환하는지(조회 전용),
 * 이미 행이 있으면 저장된 값을 그대로 반환하는지를 검증한다.
 * (잔액 행 생성은 온보딩 커맨드 경로가 담당하므로 조회는 쓰기 부수효과가 없다.)
 */
class GetCoinBalanceE2ETest : AbstractIntegrationSupport({

	describe("GET /coins/v1/balance") {

		context("잔액 행이 아직 없는 사용자가 조회하면") {
			it("0 잔액을 반환하고 잔액 행을 만들지 않는다 (조회는 쓰기를 하지 않는다)") {
				// 적립 이력이 없는 신규 사용자 - 잔액 행을 미리 만들지 않는다.
				val userId: Long = 1001L

				get("/coins/v1/balance") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("success", true)
					body("data.balance", 0)
				}

				// 조회는 쓰기를 하지 않으므로 잔액 행이 생기지 않는다.
				coinBalanceCountOf(userId) shouldBe 0
			}
		}

		context("이미 잔액 행이 있는 사용자가 조회하면") {
			it("저장된 잔액을 그대로 반환하고 새 행을 만들지 않는다") {
				val userId: Long = 1002L
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = userId, balance = 70))

				get("/coins/v1/balance") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.balance", 70)
				}

				coinBalanceCountOf(userId) shouldBe 1
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QCoinBalanceEntity.coinBalanceEntity)
	}
})

// 해당 사용자의 잔액 행 개수. (신규 생성/중복 생성 여부 확인용)
private fun coinBalanceCountOf(userId: Long): Int {
	val balance: QCoinBalanceEntity = QCoinBalanceEntity.coinBalanceEntity
	return IntegrationUtil.getQuery()
		.selectFrom(balance)
		.where(balance.userId.eq(userId))
		.fetch()
		.size
}
