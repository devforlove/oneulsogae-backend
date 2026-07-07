package com.org.meeple.api.admin

import com.org.meeple.common.gathering.GatheringScheduleStatus
import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.common.gathering.GatheringType
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.infra.fixture.GatheringEntityFixture
import com.org.meeple.infra.fixture.GatheringScheduleEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.gathering.command.entity.QGatheringEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringScheduleEntity
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.notNullValue
import java.time.LocalDateTime

/**
 * 어드민 모임 조회 API E2E 테스트.
 * - GET /admin/v1/gatherings: 최신순(created_at desc, id desc) 페이징. 목록 행은 소개·참가비 상세 제외.
 * - GET /admin/v1/gatherings/{id}: 상세(소개·참가비 상세 포함), 없는 id 404(GATHER-008).
 */
class AdminGatheringQueryE2ETest : AbstractIntegrationSupport({

	describe("GET /admin/v1/gatherings") {

		it("최신순으로 페이징 조회하고 목록 행은 소개·참가비 상세를 노출하지 않는다") {
			IntegrationUtil.persist(GatheringEntityFixture.create(title = "첫 모임"))
			val lastId: Long = IntegrationUtil.persist(
				GatheringEntityFixture.create(title = "둘째 모임", type = GatheringType.COOKING),
			).id!!

			get("/admin/v1/gatherings") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("success", true)
				body("data.totalElements", 2)
				body("data.content", hasSize<Any>(2))
				// 최신순: 마지막 저장분이 먼저.
				body("data.content[0].id", lastId.toInt())
				body("data.content[0].title", "둘째 모임")
				body("data.content[0].type", "COOKING")
				body("data.content[0].status", "RECRUITING")
				// 인원(최소·최대)은 목록 행에도 노출한다.
				body("data.content[0].minParticipants", 2)
				body("data.content[0].maxParticipants", 4)
				// 대표 이미지는 목록 행에도 노출한다.
				body("data.content[0].imageUrl", notNullValue())
				// 목록 행에 소개·참가비 상세는 없다.
				body("data.content[0].description", null)
				body("data.content[0].maleFee", null)
			}
		}

		it("size로 페이지 크기를 제한한다") {
			(1..3).forEach { index: Int ->
				IntegrationUtil.persist(GatheringEntityFixture.create(title = "모임-$index"))
			}

			get("/admin/v1/gatherings?page=0&size=2") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.content", hasSize<Any>(2))
				body("data.size", 2)
				body("data.totalElements", 3)
				body("data.totalPages", 2)
				body("data.hasNext", true)
			}
		}

		it("status로 상태를 필터한다") {
			IntegrationUtil.persist(GatheringEntityFixture.create(title = "활성화 모임", status = GatheringStatus.RECRUITING))
			IntegrationUtil.persist(GatheringEntityFixture.create(title = "취소 모임", status = GatheringStatus.CANCELED))

			get("/admin/v1/gatherings?status=CANCELED") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.totalElements", 1)
				body("data.content", hasSize<Any>(1))
				body("data.content[0].title", "취소 모임")
				body("data.content[0].status", "CANCELED")
			}
		}

		it("type으로 종류를 필터한다") {
			IntegrationUtil.persist(GatheringEntityFixture.create(title = "파티 모임", type = GatheringType.PARTY))
			IntegrationUtil.persist(GatheringEntityFixture.create(title = "쿠킹 모임", type = GatheringType.COOKING))

			get("/admin/v1/gatherings?type=COOKING") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.totalElements", 1)
				body("data.content[0].title", "쿠킹 모임")
				body("data.content[0].type", "COOKING")
			}
		}

		it("status와 type을 함께 필터한다") {
			IntegrationUtil.persist(
				GatheringEntityFixture.create(title = "대상", type = GatheringType.PARTY, status = GatheringStatus.RECRUITING),
			)
			IntegrationUtil.persist(
				GatheringEntityFixture.create(title = "종류 불일치", type = GatheringType.COOKING, status = GatheringStatus.RECRUITING),
			)
			IntegrationUtil.persist(
				GatheringEntityFixture.create(title = "상태 불일치", type = GatheringType.PARTY, status = GatheringStatus.CANCELED),
			)

			get("/admin/v1/gatherings?status=RECRUITING&type=PARTY") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.totalElements", 1)
				body("data.content[0].title", "대상")
			}
		}
	}

	describe("GET /admin/v1/gatherings/{id}") {

		it("모임 상세를 소개·일정(참가비 포함) 목록과 함께 반환한다 (200)") {
			val id: Long = IntegrationUtil.persist(
				GatheringEntityFixture.create(
					title = "상세 모임",
					description = "상세 소개",
					imageKey = "gatherings/detail.png",
					region = "서울 마포구",
					minParticipants = 3,
					maxParticipants = 8,
				),
			).id!!
			// 일정 2건(시작 시각 다르게)을 넣어 시작 시각 오름차순으로 내려오는지 + 참가비가 일정에 실리는지 확인한다.
			IntegrationUtil.persist(
				GatheringScheduleEntityFixture.create(
					gatheringId = id,
					startAt = LocalDateTime.of(2999, 6, 1, 18, 0, 0),
					status = GatheringScheduleStatus.COMPLETED,
				),
			)
			IntegrationUtil.persist(
				GatheringScheduleEntityFixture.create(
					gatheringId = id,
					startAt = LocalDateTime.of(2999, 1, 1, 18, 0, 0),
					maleFee = 10000,
					femaleFee = 8000,
					earlyBirdDiscountRate = 30,
					earlyBirdCapacity = 5,
					discountMaleFee = 9000,
					discountFemaleFee = 7000,
					status = GatheringScheduleStatus.SCHEDULED,
				),
			)

			get("/admin/v1/gatherings/$id") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				body("data.id", id.toInt())
				body("data.title", "상세 모임")
				body("data.description", "상세 소개")
				// dao가 채운 image_key를 서비스가 presigned URL로 변환해 내려준다. (테스트 페이크: https://presigned.test/{key})
				body("data.imageUrl", "https://presigned.test/gatherings/detail.png")
				body("data.region", "서울 마포구")
				body("data.minParticipants", 3)
				body("data.maxParticipants", 8)
				// 일정 목록은 시작 시각 오름차순으로 내려오고, 참가비는 각 일정에 실린다.
				body("data.schedules", hasSize<Any>(2))
				body("data.schedules.status", contains("SCHEDULED", "COMPLETED"))
				body("data.schedules[0].startAt", "2999-01-01T18:00:00")
				body("data.schedules[0].statusDescription", "예정")
				body("data.schedules[0].maleFee", 10000)
				body("data.schedules[0].femaleFee", 8000)
				body("data.schedules[0].earlyBirdDiscountRate", 30)
				body("data.schedules[0].earlyBirdCapacity", 5)
				body("data.schedules[0].earlyBirdRemaining", 5)
				body("data.schedules[0].discountFemaleFee", 7000)
			}
		}

		it("일정이 없으면 schedules가 빈 배열이다 (200)") {
			val id: Long = IntegrationUtil.persist(
				GatheringEntityFixture.create(title = "일정 없는 모임", imageKey = null),
			).id!!

			get("/admin/v1/gatherings/$id") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(200)
				// 대표 이미지가 없으면 imageUrl도 null.
				body("data.imageUrl", null)
				// 일정이 없으면 빈 배열.
				body("data.schedules", hasSize<Any>(0))
			}
		}

		it("없는 id면 404다 (GATHER-008)") {
			get("/admin/v1/gatherings/999999") {
				bearer(adminAccessTokenFor(9901L))
			} expect {
				status(404)
				body("success", false)
				body("error.code", "GATHER-008")
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QGatheringScheduleEntity.gatheringScheduleEntity)
		IntegrationUtil.deleteAll(QGatheringEntity.gatheringEntity)
	}
})
