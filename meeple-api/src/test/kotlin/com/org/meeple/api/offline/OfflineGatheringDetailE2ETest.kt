package com.org.meeple.api.offline

import com.org.meeple.common.gathering.GatheringScheduleStatus
import com.org.meeple.common.gathering.GatheringStatus
import com.org.meeple.common.gathering.GatheringType
import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.get
import com.org.meeple.common.user.Gender
import com.org.meeple.infra.fixture.GatheringEntityFixture
import com.org.meeple.infra.fixture.GatheringScheduleEntityFixture
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.gathering.command.entity.QGatheringEntity
import com.org.meeple.infra.gathering.command.entity.QGatheringScheduleEntity
import com.org.meeple.infra.user.command.entity.QUserDetailEntity
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
				// 비로그인이라 각 일정이 남/녀 두 아이템으로 펼쳐진다. (일정 시작 오름차순, 같은 일정은 남성→여성)
				body("data.schedules", hasSize<Any>(4))
				body("data.schedules.gender", contains("MALE", "FEMALE", "MALE", "FEMALE"))
				// 첫 일정(2999-01-01) 남성 아이템 — 해당 성별의 참가비만 담는다.
				body("data.schedules[0].gender", "MALE")
				body("data.schedules[0].genderDescription", "남성")
				body("data.schedules[0].startAt", "2999-01-01T18:00:00")
				body("data.schedules[0].statusDescription", "예정")
				body("data.schedules[0].fee", 10000)
				body("data.schedules[0].earlyBirdFee", 7000)
				body("data.schedules[0].earlyBirdCapacity", 5)
				body("data.schedules[0].earlyBirdRemaining", 5)
				body("data.schedules[0].discountFee", 9000)
				// 같은 일정의 여성 아이템 — 여성 참가비.
				body("data.schedules[1].gender", "FEMALE")
				body("data.schedules[1].fee", 8000)
				body("data.schedules[1].earlyBirdFee", 5000)
				body("data.schedules[1].discountFee", 7000)
				// 두 번째 일정(2999-06-01, 특가 없음) 남성 아이템.
				body("data.schedules[2].gender", "MALE")
				body("data.schedules[2].statusDescription", "종료")
				body("data.schedules[2].fee", 10000)
				body("data.schedules[2].earlyBirdFee", null)
				// 비로그인 조회이므로 조회자 성별은 null이다.
				body("data.viewerGender", null)
			}
		}

		it("로그인 조회는 viewerGender와 그 성별 일정 아이템만 내려준다") {
			val userId = 7001L
			IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId, gender = Gender.MALE))
			val id: Long = IntegrationUtil.persist(
				GatheringEntityFixture.create(title = "성별 확인 모임", status = GatheringStatus.RECRUITING),
			).id!!
			IntegrationUtil.persist(
				GatheringScheduleEntityFixture.create(
					gatheringId = id,
					startAt = LocalDateTime.of(2999, 1, 1, 18, 0, 0),
					maleFee = 10000,
					femaleFee = 8000,
				),
			)

			get("/offline/v1/gatherings/$id") {
				bearer(accessTokenFor(userId))
			} expect {
				status(200)
				body("data.viewerGender", "MALE")
				// 로그인 성별(MALE) 아이템만 — 일정 1건이라 아이템도 1개.
				body("data.schedules", hasSize<Any>(1))
				body("data.schedules[0].gender", "MALE")
				body("data.schedules[0].fee", 10000)
			}
		}

		it("로그인했지만 프로필(성별)이 없으면 viewerGender는 null이다") {
			val id: Long = IntegrationUtil.persist(
				GatheringEntityFixture.create(title = "프로필 없는 조회자", status = GatheringStatus.RECRUITING),
			).id!!

			get("/offline/v1/gatherings/$id") {
				bearer(accessTokenFor(7002L))
			} expect {
				status(200)
				body("data.viewerGender", null)
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
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
	}
})
