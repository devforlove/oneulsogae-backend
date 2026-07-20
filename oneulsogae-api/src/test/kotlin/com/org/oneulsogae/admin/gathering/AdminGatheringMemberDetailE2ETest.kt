package com.org.oneulsogae.admin.gathering
import io.kotest.core.annotation.Ignored

import com.org.oneulsogae.common.gathering.GatheringMemberStatus
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.fixture.GatheringEntityFixture
import com.org.oneulsogae.infra.fixture.GatheringMemberEntityFixture
import com.org.oneulsogae.infra.fixture.GatheringProfileEntityFixture
import com.org.oneulsogae.infra.fixture.GatheringScheduleEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.gathering.command.entity.QGatheringEntity
import com.org.oneulsogae.infra.gathering.command.entity.QGatheringMemberEntity
import com.org.oneulsogae.infra.gathering.command.entity.QGatheringProfileEntity
import com.org.oneulsogae.infra.gathering.command.entity.QGatheringScheduleEntity
import com.org.oneulsogae.infra.user.command.entity.QUserEntity
import org.hamcrest.Matchers.notNullValue

/**
 * `GET /admin/v1/gatherings/schedules/{scheduleId}/members/{memberId}` E2E 테스트.
 * 신청 유저의 모임 프로필(직종·직장상세·나이·키·프로필이미지)을 gathering_profile에서 조회한다.
 * 신청이 없으면 404(GATHER-019), 프로필이 없으면(멤버 인증 미승인) 필드가 null. ROLE_ADMIN 전용.
 */
@Ignored  // [모임 미노출] 모임 엔드포인트 404로 비활성화. 재노출 시 제거.
class AdminGatheringMemberDetailE2ETest : AbstractIntegrationSupport({

	describe("GET /admin/v1/gatherings/schedules/{scheduleId}/members/{memberId}") {

		it("신청 유저의 gathering_profile 프로필을 반환한다") {
			val gatheringId: Long = IntegrationUtil.persist(GatheringEntityFixture.create()).id!!
			val scheduleId: Long = IntegrationUtil.persist(GatheringScheduleEntityFixture.create(gatheringId = gatheringId)).id!!
			val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "member-detail")).id!!
			IntegrationUtil.persist(
				GatheringProfileEntityFixture.create(
					userId = userId,
					jobCategory = "IT·개발직",
					jobDetail = "오늘의 소개 백엔드 개발자",
					height = 175,
					profileImageCode = "img_03",
				),
			)
			val memberId: Long = IntegrationUtil.persist(
				GatheringMemberEntityFixture.create(
					gatheringId = gatheringId, scheduleId = scheduleId, userId = userId,
					gender = Gender.MALE, status = GatheringMemberStatus.PENDING,
				),
			).id!!

			get("/admin/v1/gatherings/schedules/$scheduleId/members/$memberId") {
				bearer(adminAccessTokenFor(1L))
			} expect {
				status(200)
				body("success", true)
				body("data.jobCategory", "IT·개발직")
				body("data.jobDetail", "오늘의 소개 백엔드 개발자")
				body("data.height", 175)
				body("data.profileImageCode", "img_03")
				body("data.age", notNullValue())
			}
		}

		it("프로필이 없는(멤버 인증 미승인) 신청이면 필드가 null이다") {
			val gatheringId: Long = IntegrationUtil.persist(GatheringEntityFixture.create()).id!!
			val scheduleId: Long = IntegrationUtil.persist(GatheringScheduleEntityFixture.create(gatheringId = gatheringId)).id!!
			val userId: Long = IntegrationUtil.persist(UserEntityFixture.create(providerId = "member-detail-noprofile")).id!!
			val memberId: Long = IntegrationUtil.persist(
				GatheringMemberEntityFixture.create(
					gatheringId = gatheringId, scheduleId = scheduleId, userId = userId,
					gender = Gender.MALE, status = GatheringMemberStatus.PENDING,
				),
			).id!!

			get("/admin/v1/gatherings/schedules/$scheduleId/members/$memberId") {
				bearer(adminAccessTokenFor(1L))
			} expect {
				status(200)
				body("data.jobCategory", null)
				body("data.age", null)
				body("data.height", null)
				body("data.profileImageCode", null)
			}
		}

		it("없는 신청이면 404(GATHER-019)다") {
			val gatheringId: Long = IntegrationUtil.persist(GatheringEntityFixture.create()).id!!
			val scheduleId: Long = IntegrationUtil.persist(GatheringScheduleEntityFixture.create(gatheringId = gatheringId)).id!!

			get("/admin/v1/gatherings/schedules/$scheduleId/members/999999") {
				bearer(adminAccessTokenFor(1L))
			} expect {
				status(404)
				body("error.code", "GATHER-019")
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QGatheringMemberEntity.gatheringMemberEntity)
		IntegrationUtil.deleteAll(QGatheringProfileEntity.gatheringProfileEntity)
		IntegrationUtil.deleteAll(QGatheringScheduleEntity.gatheringScheduleEntity)
		IntegrationUtil.deleteAll(QGatheringEntity.gatheringEntity)
		IntegrationUtil.deleteAll(QUserEntity.userEntity)
	}
})
