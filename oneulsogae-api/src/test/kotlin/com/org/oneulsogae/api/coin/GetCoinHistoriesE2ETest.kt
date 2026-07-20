package com.org.oneulsogae.api.coin

import com.org.oneulsogae.common.coin.CoinGetType
import com.org.oneulsogae.common.coin.CoinUsageType
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.infra.coin.command.entity.CoinHistoryEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinHistoryEntity
import com.org.oneulsogae.infra.fixture.CoinHistoryEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue

/**
 * `GET /coins/v1/histories` E2E 테스트.
 *
 * 사용/획득 거래 내역 전체가 최신순으로 50건씩 커서 페이지네이션되는지 검증한다.
 * (다음 페이지는 이전 응답의 nextCursor로 잇고, 마지막 페이지는 hasNext=false·nextCursor=null)
 */
class GetCoinHistoriesE2ETest : AbstractIntegrationSupport({

	describe("GET /coins/v1/histories") {

		context("획득·사용이 섞인 내역이 55건 있으면") {
			it("첫 페이지는 최신순 50건과 nextCursor를, 커서로 이은 두 번째 페이지는 나머지 5건을 내려준다 (200)") {
				val userId: Long = 7301L
				// 획득(양수)과 사용(음수)을 섞어 55건 적재한다. 타인(7302L) 내역은 목록에 섞이면 안 된다.
				val persisted: List<CoinHistoryEntity> = (1..55).map { i: Int ->
					if (i % 2 == 0) {
						IntegrationUtil.persist(CoinHistoryEntityFixture.create(userId = userId, amount = 10, coinGetType = CoinGetType.DAILY))
					} else {
						IntegrationUtil.persist(
							CoinHistoryEntityFixture.create(userId = userId, amount = -32, coinGetType = null, coinUsageType = CoinUsageType.DATING_INIT),
						)
					}
				}
				IntegrationUtil.persist(CoinHistoryEntityFixture.create(userId = 7302L))
				val newestId: Long = persisted.last().id!!
				val fiftiethId: Long = persisted[5].id!! // 최신순 50번째 = 적재순 6번째

				get("/coins/v1/histories") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("success", true)
					body("data.content.size()", 50)
					// 최신(id 내림차순)순: 첫 행은 가장 마지막에 적재된 내역(사용 내역, 음수 amount)
					body("data.content[0].id", newestId.toInt())
					body("data.content[0].amount", -32)
					body("data.content[0].coinUsageType", CoinUsageType.DATING_INIT.name)
					body("data.content[0].coinGetType", nullValue())
					body("data.content[0].occurredAt", notNullValue())
					body("data.content[1].amount", 10)
					body("data.content[1].coinGetType", CoinGetType.DAILY.name)
					body("data.content[1].coinUsageType", nullValue())
					body("data.hasNext", true)
					body("data.nextCursor", fiftiethId.toInt())
				}

				// 첫 페이지의 nextCursor(최신순 50번째 내역 id)로 과거 구간을 잇는다.
				get("/coins/v1/histories?cursor=$fiftiethId") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("data.content.size()", 5)
					body("data.hasNext", false)
					body("data.nextCursor", nullValue())
				}
			}
		}

		context("내역이 없으면") {
			it("빈 목록과 hasNext=false를 내려준다 (200)") {
				get("/coins/v1/histories") {
					bearer(accessTokenFor(7303L))
				} expect {
					status(200)
					body("data.content.size()", 0)
					body("data.hasNext", false)
					body("data.nextCursor", nullValue())
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QCoinHistoryEntity.coinHistoryEntity)
	}
})
