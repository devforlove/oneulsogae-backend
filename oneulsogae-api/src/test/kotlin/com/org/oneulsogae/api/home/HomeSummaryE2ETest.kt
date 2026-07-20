package com.org.oneulsogae.api.home

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.infra.alarm.command.entity.QAlarmEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinBalanceEntity
import com.org.oneulsogae.infra.fixture.AlarmEntityFixture
import com.org.oneulsogae.infra.fixture.CoinBalanceEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import java.time.LocalDateTime

/**
 * `GET /home/v1/summary` E2E 테스트.
 *
 * 메인 화면 진입 시 코인 잔액과 미수신(읽지 않은) 알람 개수를 한 번에 반환한다.
 * 미수신 개수는 알람 목록과 동일하게 최근 1개월 이내의 읽지 않은 알람만 센다.
 * (읽은 알람·1개월 초과 알람·타인 알람은 개수에서 제외)
 */
class HomeSummaryE2ETest : AbstractIntegrationSupport({

	describe("GET /home/v1/summary") {

		context("코인 잔액과 미읽음/읽음/오래된/타인 알람이 섞여 있으면") {
			it("코인 잔액과, 최근 1개월 이내 읽지 않은 본인 알람 개수만 반환한다") {
				val userId: Long = 5101L
				IntegrationUtil.persist(CoinBalanceEntityFixture.create(userId = userId, balance = 120))

				val now: LocalDateTime = LocalDateTime.now()
				// 최근 1개월 미읽음 2건 → 카운트 대상
				persistAlarmAt(userId, isRead = false, createdAt = now.minusDays(2))
				persistAlarmAt(userId, isRead = false, createdAt = now.minusDays(20))
				// 읽은 알람 → 제외
				persistAlarmAt(userId, isRead = true, createdAt = now.minusDays(1))
				// 1개월 초과 미읽음 → 제외
				persistAlarmAt(userId, isRead = false, createdAt = now.minusDays(40))
				// 타인의 미읽음 알람 → 제외
				persistAlarmAt(9101L, isRead = false, createdAt = now.minusDays(1))

				get("/home/v1/summary") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("success", true)
					body("data.coinBalance", 120)
					body("data.unreadAlarmCount", 2)
				}
			}
		}

		context("잔액 행도 알람도 없는 신규 사용자가 조회하면") {
			it("코인 잔액 0과 미수신 알람 개수 0을 반환한다") {
				get("/home/v1/summary") {
					bearer(accessTokenFor(5102L))
				} expect {
					status(200)
					body("success", true)
					body("data.coinBalance", 0)
					body("data.unreadAlarmCount", 0)
				}
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				get("/home/v1/summary") {} expect {
					status(401)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QAlarmEntity.alarmEntity)
		IntegrationUtil.deleteAll(QCoinBalanceEntity.coinBalanceEntity)
	}
})

// 알람을 저장한 뒤 생성 시각(created_at)을 원하는 값으로 백데이트한다.
// created_at은 JPA Auditing이 저장 시 now로 채우므로, 1개월 컷오프 검증을 위해 QueryDSL 업데이트로 덮어쓴다.
private fun persistAlarmAt(userId: Long, isRead: Boolean, createdAt: LocalDateTime) {
	val saved = IntegrationUtil.persist(AlarmEntityFixture.create(userId = userId, isRead = isRead))
	val id: Long = saved.id!!
	val alarm: QAlarmEntity = QAlarmEntity.alarmEntity
	IntegrationUtil.update { query ->
		query.update(alarm)
			.set(alarm.createdAt, createdAt)
			.where(alarm.id.eq(id))
			.execute()
	}
}
