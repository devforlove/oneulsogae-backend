package com.org.oneulsogae.api.alarm

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.put
import com.org.oneulsogae.infra.alarm.command.entity.QAlarmEntity
import com.org.oneulsogae.infra.fixture.AlarmEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime

/**
 * `PUT /alarms/v1/read` E2E 테스트.
 *
 * 알림 페이지 진입 시 호출되어, 최근 1개월 이내의 읽지 않은 본인 알람을 모두 읽음 처리한다.
 * 1개월 초과 알람과 타인 알람은 그대로 두고, 이미 읽은 알람은 영향받지 않는다(멱등).
 */
class MarkAlarmsReadE2ETest : AbstractIntegrationSupport({

	describe("PUT /alarms/v1/read") {

		context("최근 1개월 이내/이전 미읽음 알람과 타인 알람이 섞여 있으면") {
			it("본인의 최근 1개월 이내 미읽음 알람만 모두 읽음 처리한다") {
				val userId: Long = 6101L
				val now: LocalDateTime = LocalDateTime.now()

				// 최근 1개월 이내 미읽음 2건 → 읽음 처리 대상
				val recentUnreadId: Long = persistAlarmAt(userId, isRead = false, createdAt = now.minusDays(2))
				val olderUnreadId: Long = persistAlarmAt(userId, isRead = false, createdAt = now.minusDays(20))
				// 이미 읽은 알람 → 그대로 (멱등)
				val alreadyReadId: Long = persistAlarmAt(userId, isRead = true, createdAt = now.minusDays(3))
				// 1개월 초과 미읽음 → 그대로
				val expiredUnreadId: Long = persistAlarmAt(userId, isRead = false, createdAt = now.minusDays(40))
				// 타인의 미읽음 알람 → 그대로
				val othersUnreadId: Long = persistAlarmAt(9201L, isRead = false, createdAt = now.minusDays(1))

				put("/alarms/v1/read") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("success", true)
				}

				// 최근 1개월 이내 본인 미읽음만 읽음 처리됐는지 확인
				isReadOf(recentUnreadId) shouldBe true
				isReadOf(olderUnreadId) shouldBe true
				isReadOf(alreadyReadId) shouldBe true
				isReadOf(expiredUnreadId) shouldBe false
				isReadOf(othersUnreadId) shouldBe false
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				put("/alarms/v1/read") {} expect {
					status(401)
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QAlarmEntity.alarmEntity)
	}
})

// 알람을 저장한 뒤 생성 시각(created_at)을 원하는 값으로 백데이트하고 id를 반환한다.
// created_at은 JPA Auditing이 저장 시 now로 채우므로, 1개월 컷오프 검증을 위해 QueryDSL 업데이트로 덮어쓴다.
private fun persistAlarmAt(userId: Long, isRead: Boolean, createdAt: LocalDateTime): Long {
	val saved = IntegrationUtil.persist(AlarmEntityFixture.create(userId = userId, isRead = isRead))
	val id: Long = saved.id!!
	val alarm: QAlarmEntity = QAlarmEntity.alarmEntity
	IntegrationUtil.update { query ->
		query.update(alarm)
			.set(alarm.createdAt, createdAt)
			.where(alarm.id.eq(id))
			.execute()
	}
	return id
}

// 해당 알람의 읽음 여부를 DB에서 직접 조회한다. (읽음 처리 반영 검증용)
private fun isReadOf(id: Long): Boolean {
	val alarm: QAlarmEntity = QAlarmEntity.alarmEntity
	return IntegrationUtil.getQuery()
		.select(alarm.isRead)
		.from(alarm)
		.where(alarm.id.eq(id))
		.fetchOne()!!
}
