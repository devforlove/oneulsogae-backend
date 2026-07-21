package com.org.oneulsogae.api.coin

import com.org.oneulsogae.common.alarm.AlarmType
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.common.popup.PopupType
import com.org.oneulsogae.infra.alarm.command.entity.AlarmEntity
import com.org.oneulsogae.infra.alarm.command.entity.QAlarmEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinBalanceEntity
import com.org.oneulsogae.infra.coin.command.entity.QCoinHistoryEntity
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.popup.command.entity.PopupEntity
import com.org.oneulsogae.infra.popup.command.entity.QPopupEntity
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

/**
 * 출석(DAILY) 코인 적립 시 본인에게 "코인 적립" 인앱 알림이 생성되는지 검증하는 E2E.
 * DAILY 코인은 `GET /popups/v1`에 일일 보상(DAILY_REWARD) 팝업이 노출될 때 하루 1회 지급된다.
 * 지급은 [com.org.oneulsogae.core.coin.command.domain.event.DailyCoinAcquired] 이벤트를 발행하고,
 * [com.org.oneulsogae.core.coin.command.application.CoinEventHandler]가 커밋 이후 알람을 저장한다.
 */
class DailyCoinAlarmE2ETest : AbstractIntegrationSupport({

	describe("GET /popups/v1 — 출석 코인 적립 알림") {

		context("일일 보상 팝업이 노출 중인 사용자가 팝업을 조회하면") {
			it("출석 코인이 적립되고 본인에게 '코인 적립' 인앱 알림이 남는다 (200)") {
				val userId = 9101L
				val now: LocalDateTime = LocalDateTime.now()
				IntegrationUtil.persist(
					PopupEntity(
						title = "출석 보상",
						displayOrder = 1,
						popUpType = PopupType.DAILY_REWARD,
						userId = userId,
						exposedFrom = now.minusDays(1),
						exposedTo = now.plusDays(1),
					),
				)

				get("/popups/v1") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
				}

				val alarms: List<AlarmEntity> = alarmsOf(userId)
				alarms.size shouldBe 1
				val alarm: AlarmEntity = alarms[0]
				alarm.type shouldBe AlarmType.COIN_DAILY_ACQUIRED
				alarm.description shouldBe "출석 코인 1개가 적립되었어요."
				alarm.link shouldBe "/"
				alarm.fromUserId shouldBe null
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QAlarmEntity.alarmEntity)
		IntegrationUtil.deleteAll(QCoinHistoryEntity.coinHistoryEntity)
		IntegrationUtil.deleteAll(QCoinBalanceEntity.coinBalanceEntity)
		IntegrationUtil.deleteAll(QPopupEntity.popupEntity)
	}
})

// 해당 사용자의 알람 목록. (알람 저장 확인용)
private fun alarmsOf(userId: Long): List<AlarmEntity> {
	val alarm: QAlarmEntity = QAlarmEntity.alarmEntity
	return IntegrationUtil.getQuery()
		.selectFrom(alarm)
		.where(alarm.userId.eq(userId))
		.fetch()
}
