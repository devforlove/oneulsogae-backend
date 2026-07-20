package com.org.oneulsogae.admin.gathering
import io.kotest.core.annotation.Ignored

import com.org.oneulsogae.common.gathering.GatheringMemberStatus
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.fixture.GatheringEntityFixture
import com.org.oneulsogae.infra.fixture.GatheringMemberEntityFixture
import com.org.oneulsogae.infra.fixture.GatheringScheduleEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.gathering.command.entity.QGatheringEntity
import com.org.oneulsogae.infra.gathering.command.entity.QGatheringMemberEntity
import com.org.oneulsogae.infra.gathering.command.entity.QGatheringScheduleEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import com.org.oneulsogae.infra.user.command.entity.QUserEntity
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.hasSize
import java.time.LocalDateTime

/**
 * `GET /admin/v1/gatherings/members` E2E 테스트.
 *
 * 모임·일정 무관 전역 참가 신청 목록을 신청 순(id 오름차순)으로 반환한다. 각 행은 어느 모임·일정의 신청인지
 * (모임명·일정시각·scheduleId) 맥락을 담는다. status 필터·페이징. ROLE_ADMIN 전용.
 *
 * 전역 조회라 다른 스펙이 남긴 참가 신청에 오염될 수 있어, 각 테스트 시작 시 gathering_member를 비워
 * 결정적인 개수를 보장한다. afterTest로 생성물을 정리한다.
 */
@Ignored  // [모임 미노출] 모임 엔드포인트 404로 비활성화. 재노출 시 제거.
class AdminGatheringMemberSearchE2ETest : AbstractIntegrationSupport({

	describe("GET /admin/v1/gatherings/members") {

		context("여러 모임·일정에 신청이 있으면") {
			it("모임 무관 전체를 신청 순으로 모임명·일정시각과 함께 반환한다") {
				IntegrationUtil.deleteAll(QGatheringMemberEntity.gatheringMemberEntity)

				val firstGatheringId: Long =
					IntegrationUtil.persist(GatheringEntityFixture.create(title = "보드게임 모임")).id!!
				val firstScheduleId: Long = IntegrationUtil.persist(
					GatheringScheduleEntityFixture.create(
						gatheringId = firstGatheringId,
						startAt = LocalDateTime.of(2999, 1, 1, 19, 0, 0),
					),
				).id!!

				val secondGatheringId: Long =
					IntegrationUtil.persist(GatheringEntityFixture.create(title = "등산 모임")).id!!
				val secondScheduleId: Long = IntegrationUtil.persist(
					GatheringScheduleEntityFixture.create(
						gatheringId = secondGatheringId,
						startAt = LocalDateTime.of(2999, 2, 2, 9, 0, 0),
					),
				).id!!

				val firstUserId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "admin-search-1")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = firstUserId, nickname = "첫째"))
				IntegrationUtil.persist(
					GatheringMemberEntityFixture.create(
						gatheringId = firstGatheringId, scheduleId = firstScheduleId, userId = firstUserId,
						gender = Gender.MALE, status = GatheringMemberStatus.PENDING,
					),
				)

				val secondUserId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "admin-search-2")).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = secondUserId, nickname = "둘째", gender = Gender.FEMALE))
				IntegrationUtil.persist(
					GatheringMemberEntityFixture.create(
						gatheringId = secondGatheringId, scheduleId = secondScheduleId, userId = secondUserId,
						gender = Gender.FEMALE, status = GatheringMemberStatus.JOINED,
					),
				)

				get("/admin/v1/gatherings/members") {
					bearer(adminAccessTokenFor(1L))
				} expect {
					status(200)
					body("success", true)
					body("data.totalElements", 2)
					body("data.content", hasSize<Any>(2))
					body("data.content.nickname", contains("첫째", "둘째"))
					body("data.content.status", contains("PENDING", "JOINED"))
					body("data.content.gatheringTitle", contains("보드게임 모임", "등산 모임"))
					body("data.content.scheduleId", contains(firstScheduleId.toInt(), secondScheduleId.toInt()))
				}

				// status 필터: PENDING만.
				get("/admin/v1/gatherings/members?status=PENDING") {
					bearer(adminAccessTokenFor(1L))
				} expect {
					status(200)
					body("data.totalElements", 1)
					body("data.content", hasSize<Any>(1))
					body("data.content[0].status", "PENDING")
					body("data.content[0].nickname", "첫째")
					body("data.content[0].gatheringTitle", "보드게임 모임")
				}
			}
		}

		context("신청이 하나도 없으면") {
			it("빈 목록을 반환한다") {
				IntegrationUtil.deleteAll(QGatheringMemberEntity.gatheringMemberEntity)

				get("/admin/v1/gatherings/members") {
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
		IntegrationUtil.deleteAll(QGatheringMemberEntity.gatheringMemberEntity)
		IntegrationUtil.deleteAll(QGatheringScheduleEntity.gatheringScheduleEntity)
		IntegrationUtil.deleteAll(QGatheringEntity.gatheringEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}
})
