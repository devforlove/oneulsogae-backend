package com.org.meeple.api.alarm

import com.org.meeple.common.alarm.AlarmType
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.infra.alarm.entity.QAlarmEntity
import com.org.meeple.infra.fixture.AlarmEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import org.hamcrest.Matchers.notNullValue
import java.time.LocalDateTime

/**
 * `GET /alarms/v1` E2E 테스트.
 *
 * 현재 로그인 사용자의 알람을 최신순(id 내림차순)으로 조회한다.
 * 실제 서버(RANDOM_PORT) + Testcontainers(MySQL)를 기동하고 HTTP를 호출한다.
 * 데이터 준비/정리는 [IntegrationUtil], 요청/검증은 [get]/[expect] Kotlin DSL로 한다.
 */
class MyAlarmsE2ETest : AbstractIntegrationSupport({

	describe("GET /alarms/v1") {

		context("최근 1개월 이내/이전 알람과 타인 알람이 섞여 있으면") {
			it("본인의 최근 1개월 이내 알람만 생성 시각 최신순으로 반환한다") {
				val userId = 7101L
				val now: LocalDateTime = LocalDateTime.now()

				// 2일 전(최신), 20일 전, 40일 전(1개월 초과 → 제외), 타인 알람 → 제외
				val recentId: Long = persistAlarmAt(userId, "2일전 알람", fromUserId = 8102L, createdAt = now.minusDays(2))
				val olderId: Long = persistAlarmAt(userId, "20일전 알람", fromUserId = 8101L, createdAt = now.minusDays(20))
				persistAlarmAt(userId, "40일전 알람", fromUserId = 8100L, createdAt = now.minusDays(40))
				persistAlarmAt(9101L, "남의 알람", fromUserId = null, createdAt = now.minusDays(1))

				get("/alarms/v1") {
					bearer(accessTokenFor(userId))
				} expect {
					status(200)
					body("success", true)
					// 1개월 초과(40일 전)·타인 알람은 빠지고 2건만
					body("data.size()", 2)
					// 생성 시각 최신순: 2일 전 → 20일 전
					body("data[0].id", recentId.toInt())
					body("data[0].title", "2일전 알람")
					body("data[0].type", AlarmType.INTEREST_RECEIVED.name)
					body("data[0].description", "회원님에게 관심을 보낸 상대가 있어요.")
					body("data[0].link", "/")
					body("data[0].fromUserId", 8102)
					body("data[0].isRead", false)
					body("data[0].createdAt", notNullValue())
					body("data[1].id", olderId.toInt())
					body("data[1].title", "20일전 알람")
				}
			}
		}

		context("알람이 없으면") {
			it("빈 목록을 반환한다") {
				get("/alarms/v1") {
					bearer(accessTokenFor(7102L))
				} expect {
					status(200)
					body("success", true)
					body("data.size()", 0)
				}
			}
		}

		context("인증 토큰이 없으면") {
			it("401을 반환한다") {
				get("/alarms/v1") {} expect {
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
// created_at은 JPA Auditing이 저장 시 now로 채우므로, 1개월 컷오프/정렬 검증을 위해 QueryDSL 업데이트로 덮어쓴다.
private fun persistAlarmAt(userId: Long, title: String, fromUserId: Long?, createdAt: LocalDateTime): Long {
	val saved = IntegrationUtil.persist(
		AlarmEntityFixture.create(userId = userId, title = title, fromUserId = fromUserId),
	)
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
