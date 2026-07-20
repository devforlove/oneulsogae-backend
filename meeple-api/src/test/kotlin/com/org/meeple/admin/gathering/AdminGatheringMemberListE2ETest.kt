package com.org.meeple.admin.gathering
import io.kotest.core.annotation.Ignored

import com.org.meeple.common.gathering.GatheringMemberStatus
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.fixture.GatheringEntityFixture
import com.org.meeple.infra.fixture.GatheringMemberEntityFixture
import com.org.meeple.infra.fixture.GatheringProfileEntityFixture
import com.org.meeple.infra.fixture.GatheringScheduleEntityFixture
import com.org.meeple.infra.fixture.MemberVerificationEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.core.payments.command.domain.PaymentStatus
import com.org.meeple.infra.gathering.command.entity.QGatheringEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringMemberEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringProfileEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringScheduleEntity
import com.org.meeple.infra.gathering.command.entity.QMemberVerificationEntity
import com.org.meeple.infra.payments.command.entity.GatheringPaymentEntity
import com.org.meeple.infra.payments.command.entity.QGatheringPaymentEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
import com.org.meeple.infra.user.command.entity.QUserEntity
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.hasSize

/**
 * `GET /admin/v1/gatherings/schedules/{scheduleId}/members` E2E 테스트.
 *
 * 일정별 참가 신청 목록(신청 id·유저·닉네임·성별·상태·결제금액·신청 시각)을 신청 순(id 오름차순)으로 반환한다.
 * 결제금액은 (schedule, user)의 최신 결제 기록에서 조인한다(재접수 시 최신 금액). ROLE_ADMIN 전용.
 */
@Ignored  // [모임 미노출] 모임 엔드포인트 404로 비활성화. 재노출 시 제거.
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
				// 첫째만 회원 인증(gathering_profile) 완료 → memberVerified true.
				IntegrationUtil.persist(GatheringProfileEntityFixture.create(userId = firstUserId))
				// 첫째의 멤버 인증 제출 → memberVerificationId로 노출된다.
				val firstVerificationId: Long = IntegrationUtil.persist(MemberVerificationEntityFixture.create(userId = firstUserId)).id!!
				IntegrationUtil.persist(
					GatheringMemberEntityFixture.create(
						gatheringId = gatheringId, scheduleId = scheduleId, userId = firstUserId,
						gender = Gender.MALE, status = GatheringMemberStatus.PENDING,
					),
				)
				IntegrationUtil.persist(
					GatheringPaymentEntity(userId = firstUserId, gatheringId = gatheringId, scheduleId = scheduleId, productId = 1L, paymentKey = "pay_key_admin_1", orderId = "ord_admin_1", gender = Gender.MALE, amount = 10000, status = PaymentStatus.APPROVED),
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
					GatheringPaymentEntity(userId = secondUserId, gatheringId = gatheringId, scheduleId = scheduleId, productId = 2L, paymentKey = "pay_key_admin_2", orderId = "ord_admin_2", gender = Gender.FEMALE, amount = 8000, status = PaymentStatus.APPROVED),
				)
				IntegrationUtil.persist(
					GatheringPaymentEntity(userId = secondUserId, gatheringId = gatheringId, scheduleId = scheduleId, productId = 2L, paymentKey = "pay_key_admin_3", orderId = "ord_admin_3", gender = Gender.FEMALE, amount = 5600, status = PaymentStatus.APPROVED),
				)

				get("/admin/v1/gatherings/schedules/$scheduleId/members") {
					bearer(adminAccessTokenFor(1L))
				} expect {
					status(200)
					body("success", true)
					body("data.totalElements", 2)
					body("data.content", hasSize<Any>(2))
					body("data.content.nickname", contains("첫째", "둘째"))
					body("data.content.status", contains("PENDING", "JOINED"))
					body("data.content.amount", contains(10000, 5600))
					body("data.content.gender", contains("MALE", "FEMALE"))
					// 첫째만 회원 인증 완료.
					body("data.content.memberVerified", contains(true, false))
					// 첫째는 멤버 인증 제출 id, 둘째는 제출 이력 없어 null.
					body("data.content.memberVerificationId", contains(firstVerificationId.toInt(), null))
				}

				// status 필터: PENDING만.
				get("/admin/v1/gatherings/schedules/$scheduleId/members?status=PENDING") {
					bearer(adminAccessTokenFor(1L))
				} expect {
					status(200)
					body("data.totalElements", 1)
					body("data.content", hasSize<Any>(1))
					body("data.content[0].status", "PENDING")
					body("data.content[0].nickname", "첫째")
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
					body("data.totalElements", 0)
					body("data.content", hasSize<Any>(0))
				}
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QMemberVerificationEntity.memberVerificationEntity)
		IntegrationUtil.deleteAll(QGatheringProfileEntity.gatheringProfileEntity)
		IntegrationUtil.deleteAll(QGatheringMemberEntity.gatheringMemberEntity)
		IntegrationUtil.deleteAll(QGatheringPaymentEntity.gatheringPaymentEntity)
		IntegrationUtil.deleteAll(QGatheringScheduleEntity.gatheringScheduleEntity)
		IntegrationUtil.deleteAll(QGatheringEntity.gatheringEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}
})
