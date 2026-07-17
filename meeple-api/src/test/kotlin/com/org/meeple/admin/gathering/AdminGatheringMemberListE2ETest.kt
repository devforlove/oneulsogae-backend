package com.org.meeple.admin.gathering

import com.org.meeple.common.gathering.GatheringMemberStatus
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.fixture.GatheringEntityFixture
import com.org.meeple.infra.fixture.GatheringMemberEntityFixture
import com.org.meeple.infra.fixture.GatheringScheduleEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.core.payments.command.domain.PaymentStatus
import com.org.meeple.infra.payments.command.entity.PaymentEntity
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.hasSize

/**
 * `GET /admin/v1/gatherings/schedules/{scheduleId}/members` E2E 테스트.
 *
 * 일정별 참가 신청 목록(신청 id·유저·닉네임·성별·상태·결제금액·신청 시각)을 신청 순(id 오름차순)으로 반환한다.
 * 결제금액은 (schedule, user)의 최신 결제 기록에서 조인한다(재접수 시 최신 금액). ROLE_ADMIN 전용.
 */
class AdminGatheringMemberListE2ETest : AbstractIntegrationSupport({

	describe("GET /admin/v1/gatherings/schedules/{scheduleId}/members") {

		context("일정에 여러 신청이 있으면") {
			it("신청 순으로 닉네임·상태·결제금액을 담아 반환한다") {
				val gatheringId: Long = IntegrationUtil.persist(GatheringEntityFixture.create()).id!!
				val scheduleId: Long = IntegrationUtil.persist(
					GatheringScheduleEntityFixture.create(gatheringId = gatheringId),
				).id!!

				val firstUserId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "admin-list-1")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = firstUserId, nickname = "첫째"))
				IntegrationUtil.persist(
					GatheringMemberEntityFixture.create(
						gatheringId = gatheringId, scheduleId = scheduleId, userId = firstUserId,
						gender = Gender.MALE, status = GatheringMemberStatus.PENDING,
					),
				)
				IntegrationUtil.persist(
					PaymentEntity(userId = firstUserId, gatheringId = gatheringId, scheduleId = scheduleId, productId = 1L, paymentKey = "pay_key_admin_1", orderId = "ord_admin_1", gender = Gender.MALE, amount = 10000, status = PaymentStatus.APPROVED),
				)

				val secondUserId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "admin-list-2")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = secondUserId, nickname = "둘째", gender = Gender.FEMALE))
				IntegrationUtil.persist(
					GatheringMemberEntityFixture.create(
						gatheringId = gatheringId, scheduleId = scheduleId, userId = secondUserId,
						gender = Gender.FEMALE, status = GatheringMemberStatus.JOINED,
					),
				)
				// 재접수 이력: 과거 8000 → 최신 5600. 최신 금액이 조인되어야 한다.
				IntegrationUtil.persist(
					PaymentEntity(userId = secondUserId, gatheringId = gatheringId, scheduleId = scheduleId, productId = 2L, paymentKey = "pay_key_admin_2", orderId = "ord_admin_2", gender = Gender.FEMALE, amount = 8000, status = PaymentStatus.APPROVED),
				)
				IntegrationUtil.persist(
					PaymentEntity(userId = secondUserId, gatheringId = gatheringId, scheduleId = scheduleId, productId = 2L, paymentKey = "pay_key_admin_3", orderId = "ord_admin_3", gender = Gender.FEMALE, amount = 5600, status = PaymentStatus.APPROVED),
				)

				get("/admin/v1/gatherings/schedules/$scheduleId/members") {
					bearer(adminAccessTokenFor(1L))
				} expect {
					status(200)
					body("success", true)
					body("data", hasSize<Any>(2))
					body("data.nickname", contains("첫째", "둘째"))
					body("data.status", contains("PENDING", "JOINED"))
					body("data.amount", contains(10000, 5600))
					body("data.gender", contains("MALE", "FEMALE"))
				}
			}
		}

		context("신청이 없는 일정이면") {
			it("빈 목록을 반환한다") {
				val gatheringId: Long = IntegrationUtil.persist(GatheringEntityFixture.create()).id!!
				val scheduleId: Long = IntegrationUtil.persist(
					GatheringScheduleEntityFixture.create(gatheringId = gatheringId),
				).id!!

				get("/admin/v1/gatherings/schedules/$scheduleId/members") {
					bearer(adminAccessTokenFor(1L))
				} expect {
					status(200)
					body("data", hasSize<Any>(0))
				}
			}
		}
	}
})
