package com.org.meeple.api.offline

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
import java.time.LocalDateTime

/**
 * 오프라인(비인증 공개) 모임 상세 조회 API E2E 테스트.
 * - GET /offline/v1/gatherings/{id}: 인증 토큰 없이 접근 가능. 모집중(RECRUITING) 한 건의 상세(소개·인원·참가비 3티어)를 반환.
 *   없거나 모집중이 아니면 404(GATHERING-001). 상태(status)는 응답에 포함하지 않는다.
 * (presigned URL은 TestFileStorageConfig의 페이크로 대체 — https://presigned.test/<imageKey>)
 */
class OfflineGatheringDetailE2ETest : AbstractIntegrationSupport({

	describe("GET /offline/v1/gatherings/{id}") {

		it("인증 토큰 없이도 모집중 모임 상세를 소개·일정(참가비 포함) 목록과 함께 반환한다") {
			val id: Long = IntegrationUtil.persist(
				GatheringEntityFixture.create(
					type = GatheringType.COOKING,
					title = "상세 모임",
					description = "상세 소개",
					imageKey = "gatherings/detail.png",
					region = "서울 마포구",
					minParticipants = 3,
					maxParticipants = 8,
					status = GatheringStatus.RECRUITING,
				),
			).id!!
			// 일정 2건(시작 시각 다르게)을 넣어 시작 시각 오름차순 + 참가비가 일정에 실리는지 확인한다.
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
					earlyBirdMaleFee = 7000,
					earlyBirdFemaleFee = 5000,
					earlyBirdCapacity = 5,
					discountMaleFee = 9000,
					discountFemaleFee = 7000,
					status = GatheringScheduleStatus.SCHEDULED,
				),
			)

			get("/offline/v1/gatherings/$id") { } expect {
				status(200)
				body("success", true)
				body("data.id", id.toInt())
				body("data.type", "COOKING")
				body("data.typeDescription", "쿠킹")
				body("data.title", "상세 모임")
				body("data.description", "상세 소개")
				body("data.imageUrl", "https://presigned.test/gatherings/detail.png")
				body("data.region", "서울 마포구")
				body("data.minParticipants", 3)
				body("data.maxParticipants", 8)
				// 상태(status)는 응답에 포함하지 않는다.
				body("data.status", null)
				// 일정 목록은 시작 시각 오름차순으로 내려오고, 참가비는 각 일정에 실린다.
				body("data.schedules", hasSize<Any>(2))
				body("data.schedules.status", contains("SCHEDULED", "COMPLETED"))
				body("data.schedules[0].startAt", "2999-01-01T18:00:00")
				body("data.schedules[0].statusDescription", "예정")
				body("data.schedules[0].maleFee", 10000)
				body("data.schedules[0].earlyBirdMaleFee", 7000)
				body("data.schedules[0].earlyBirdCapacity", 5)
				body("data.schedules[0].discountMaleFee", 9000)
			}
		}

		it("일정이 없으면 schedules가 빈 배열이고, 대표 이미지가 없으면 imageUrl도 null이다") {
			val id: Long = IntegrationUtil.persist(
				GatheringEntityFixture.create(
					title = "일정 없는 모임",
					status = GatheringStatus.RECRUITING,
					imageKey = null,
				),
			).id!!

			get("/offline/v1/gatherings/$id") { } expect {
				status(200)
				body("data.imageUrl", null)
				// 일정이 없으면 빈 배열.
				body("data.schedules", hasSize<Any>(0))
			}
		}

		it("활성화가 아닌 모임(취소 등)은 404다 (GATHERING-001)") {
			val id: Long = IntegrationUtil.persist(
				GatheringEntityFixture.create(title = "취소됨", status = GatheringStatus.CANCELED),
			).id!!

			get("/offline/v1/gatherings/$id") { } expect {
				status(404)
				body("success", false)
				body("error.code", "GATHERING-001")
			}
		}

		it("없는 id면 404다 (GATHERING-001)") {
			get("/offline/v1/gatherings/999999") { } expect {
				status(404)
				body("success", false)
				body("error.code", "GATHERING-001")
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QGatheringScheduleEntity.gatheringScheduleEntity)
		IntegrationUtil.deleteAll(QGatheringEntity.gatheringEntity)
	}
})
