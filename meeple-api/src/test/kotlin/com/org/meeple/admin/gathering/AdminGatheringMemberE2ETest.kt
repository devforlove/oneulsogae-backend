package com.org.meeple.admin.gathering

import com.org.meeple.common.gathering.GatheringMemberStatus
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.post
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.fixture.GatheringEntityFixture
import com.org.meeple.infra.fixture.GatheringMemberEntityFixture
import com.org.meeple.infra.fixture.GatheringProductEntityFixture
import com.org.meeple.infra.fixture.GatheringProfileEntityFixture
import com.org.meeple.infra.fixture.GatheringScheduleEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.gathering.command.entity.GatheringMemberEntity
import com.org.meeple.infra.gathering.command.entity.GatheringProductEntity
import com.org.meeple.infra.gathering.command.entity.GatheringScheduleEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringMemberEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringProductEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringProfileEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringScheduleEntity
import io.kotest.matchers.shouldBe

/**
 * `POST /admin/v1/gatherings/schedules/{scheduleId}/members/{memberId}/approve|reject` E2E 테스트.
 *
 * 승인: PENDING → JOINED (여분 변화 없음 — 접수 시 이미 차감).
 * 거절: PENDING → REJECTED + 성별 여분(얼리버드 적용분 포함) 복원.
 * PENDING 아닌 신청 409(GATHER-020), 없는 신청/일정 불일치 404(GATHER-019). ROLE_ADMIN 전용.
 */
class AdminGatheringMemberE2ETest : AbstractIntegrationSupport({

	// 일정(여분 3/4 — 접수 1건 차감 상태) + PENDING 참가 신청을 저장하고 (scheduleId, memberId)를 돌려준다.
	// [verified]면 신청 유저(1000L)의 회원 인증(gathering_profile)도 함께 심는다(승인 가능 상태).
	fun persistPendingMember(earlyBirdApplied: Boolean = false, verified: Boolean = true): Pair<Long, Long> {
		val gatheringId: Long = IntegrationUtil.persist(GatheringEntityFixture.create()).id!!
		val scheduleId: Long = IntegrationUtil.persist(
			GatheringScheduleEntityFixture.create(
				gatheringId = gatheringId,
				maleCapacity = 4,
				maleRemaining = 3,
				earlyBirdCapacity = if (earlyBirdApplied) 2 else null,
				earlyBirdRemaining = if (earlyBirdApplied) 1 else null,
			),
		).id!!
		GatheringProductEntityFixture.tierSet(
			gatheringId = gatheringId,
			scheduleId = scheduleId,
			earlyBirdDiscountRate = if (earlyBirdApplied) 30 else null,
		).forEach { product: GatheringProductEntity -> IntegrationUtil.persist(product) }
		if (verified) {
			IntegrationUtil.persist(GatheringProfileEntityFixture.create(userId = 1000L))
		}
		val memberId: Long = IntegrationUtil.persist(
			GatheringMemberEntityFixture.create(
				gatheringId = gatheringId,
				scheduleId = scheduleId,
				userId = 1000L,
				gender = Gender.MALE,
				status = GatheringMemberStatus.PENDING,
				earlyBirdApplied = earlyBirdApplied,
			),
		).id!!
		return scheduleId to memberId
	}

	fun findMember(memberId: Long): GatheringMemberEntity? {
		val member: QGatheringMemberEntity = QGatheringMemberEntity.gatheringMemberEntity
		return IntegrationUtil.getQuery().selectFrom(member).where(member.id.eq(memberId)).fetchOne()
	}

	fun findSchedule(scheduleId: Long): GatheringScheduleEntity? {
		val schedule: QGatheringScheduleEntity = QGatheringScheduleEntity.gatheringScheduleEntity
		return IntegrationUtil.getQuery().selectFrom(schedule).where(schedule.id.eq(scheduleId)).fetchOne()
	}

	describe("POST /admin/v1/gatherings/schedules/{scheduleId}/members/{memberId}/approve") {

		context("승인대기 신청을 승인하면") {
			it("참가(JOINED)로 전이하고 여분은 바꾸지 않는다") {
				val (scheduleId: Long, memberId: Long) = persistPendingMember()

				post("/admin/v1/gatherings/schedules/$scheduleId/members/$memberId/approve") {
					bearer(adminAccessTokenFor(1L))
				} expect {
					status(200)
					body("success", true)
				}

				findMember(memberId)?.status shouldBe GatheringMemberStatus.JOINED
				findSchedule(scheduleId)?.maleRemaining shouldBe 3
			}
		}

		context("이미 참가 상태인 신청을 승인하면") {
			it("409 GATHER-020을 반환한다") {
				val (scheduleId: Long, memberId: Long) = persistPendingMember()
				post("/admin/v1/gatherings/schedules/$scheduleId/members/$memberId/approve") {
					bearer(adminAccessTokenFor(1L))
				} expect { status(200) }

				post("/admin/v1/gatherings/schedules/$scheduleId/members/$memberId/approve") {
					bearer(adminAccessTokenFor(1L))
				} expect {
					status(409)
					body("error.code", "GATHER-020")
				}
			}
		}

		context("회원 인증(gathering_profile)이 없는 유저의 신청을 승인하면") {
			it("409 GATHER-021을 반환하고 상태를 바꾸지 않는다") {
				val (scheduleId: Long, memberId: Long) = persistPendingMember(verified = false)

				post("/admin/v1/gatherings/schedules/$scheduleId/members/$memberId/approve") {
					bearer(adminAccessTokenFor(1L))
				} expect {
					status(409)
					body("error.code", "GATHER-021")
				}

				findMember(memberId)?.status shouldBe GatheringMemberStatus.PENDING
			}
		}

		context("다른 일정의 memberId로 승인하면") {
			it("404 GATHER-019를 반환한다") {
				val (_, memberId: Long) = persistPendingMember()

				post("/admin/v1/gatherings/schedules/999999/members/$memberId/approve") {
					bearer(adminAccessTokenFor(1L))
				} expect {
					status(404)
					body("error.code", "GATHER-019")
				}
			}
		}

		context("일반 유저 토큰으로 승인하면") {
			it("403을 반환한다") {
				val (scheduleId: Long, memberId: Long) = persistPendingMember()

				post("/admin/v1/gatherings/schedules/$scheduleId/members/$memberId/approve") {
					bearer(accessTokenFor(2L))
				} expect {
					status(403)
				}
			}
		}
	}

	describe("POST /admin/v1/gatherings/schedules/{scheduleId}/members/{memberId}/reject") {

		context("얼리버드가 적용된 승인대기 신청을 거절하면") {
			it("거절(REJECTED)로 전이하고 성별·얼리버드 여분을 복원한다") {
				val (scheduleId: Long, memberId: Long) = persistPendingMember(earlyBirdApplied = true)

				post("/admin/v1/gatherings/schedules/$scheduleId/members/$memberId/reject") {
					bearer(adminAccessTokenFor(1L))
				} expect {
					status(200)
				}

				findMember(memberId)?.status shouldBe GatheringMemberStatus.REJECTED
				val schedule: GatheringScheduleEntity? = findSchedule(scheduleId)
				schedule?.maleRemaining shouldBe 4
				schedule?.earlyBirdRemaining shouldBe 2
			}
		}

		context("얼리버드가 아닌 승인대기 신청을 거절하면") {
			it("성별 여분만 복원한다") {
				val (scheduleId: Long, memberId: Long) = persistPendingMember()

				post("/admin/v1/gatherings/schedules/$scheduleId/members/$memberId/reject") {
					bearer(adminAccessTokenFor(1L))
				} expect {
					status(200)
				}

				findMember(memberId)?.status shouldBe GatheringMemberStatus.REJECTED
				findSchedule(scheduleId)?.maleRemaining shouldBe 4
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QGatheringMemberEntity.gatheringMemberEntity)
		IntegrationUtil.deleteAll(QGatheringProfileEntity.gatheringProfileEntity)
		IntegrationUtil.deleteAll(QGatheringProductEntity.gatheringProductEntity)
	}
})
