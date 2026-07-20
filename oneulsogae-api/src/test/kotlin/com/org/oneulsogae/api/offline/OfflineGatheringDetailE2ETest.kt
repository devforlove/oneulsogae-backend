package com.org.oneulsogae.api.offline
import io.kotest.core.annotation.Ignored

import com.org.oneulsogae.common.gathering.GatheringMemberStatus
import com.org.oneulsogae.common.gathering.GatheringProductType
import com.org.oneulsogae.common.gathering.GatheringScheduleStatus
import com.org.oneulsogae.common.gathering.GatheringStatus
import com.org.oneulsogae.common.gathering.GatheringType
import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.get
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.infra.fixture.GatheringEntityFixture
import com.org.oneulsogae.infra.fixture.GatheringMemberEntityFixture
import com.org.oneulsogae.infra.fixture.GatheringProductEntityFixture
import com.org.oneulsogae.infra.fixture.GatheringProfileEntityFixture
import com.org.oneulsogae.infra.fixture.GatheringScheduleEntityFixture
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.gathering.command.entity.GatheringProductEntity
import com.org.oneulsogae.infra.gathering.command.entity.QGatheringEntity
import com.org.oneulsogae.infra.gathering.command.entity.QGatheringMemberEntity
import com.org.oneulsogae.infra.gathering.command.entity.QGatheringProductEntity
import com.org.oneulsogae.infra.gathering.command.entity.QGatheringProfileEntity
import com.org.oneulsogae.infra.gathering.command.entity.QGatheringScheduleEntity
import com.org.oneulsogae.infra.user.command.entity.QUserDetailEntity
import java.time.LocalDate
import org.hamcrest.Matchers.contains
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.notNullValue
import java.time.LocalDateTime

/**
 * 오프라인(비인증 공개) 모임 상세 조회 API E2E 테스트.
 * - GET /offline/v1/gatherings/{id}: 인증 토큰 없이 접근 가능. 모집중(RECRUITING) 한 건의 상세(소개·인원·참가비 3티어)를 반환.
 *   없거나 모집중이 아니면 404(GATHERING-001). 상태(status)는 응답에 포함하지 않는다.
 * (presigned URL은 TestFileStorageConfig의 페이크로 대체 — https://presigned.test/<imageKey>)
 */
@Ignored  // [모임 미노출] 모임 엔드포인트 404로 비활성화. 재노출 시 제거.
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
			val completedScheduleId: Long = IntegrationUtil.persist(
				GatheringScheduleEntityFixture.create(
					gatheringId = id,
					startAt = LocalDateTime.of(2999, 6, 1, 18, 0, 0),
					status = GatheringScheduleStatus.COMPLETED,
				),
			).id!!
			GatheringProductEntityFixture.tierSet(gatheringId = id, scheduleId = completedScheduleId)
				.forEach { product: GatheringProductEntity -> IntegrationUtil.persist(product) }
			val scheduledScheduleId: Long = IntegrationUtil.persist(
				GatheringScheduleEntityFixture.create(
					gatheringId = id,
					startAt = LocalDateTime.of(2999, 1, 1, 18, 0, 0),
					earlyBirdCapacity = 5,
					status = GatheringScheduleStatus.SCHEDULED,
				),
			).id!!
			val products: List<GatheringProductEntity> = GatheringProductEntityFixture.tierSet(
				gatheringId = id,
				scheduleId = scheduledScheduleId,
				maleFee = 10000,
				femaleFee = 8000,
				earlyBirdDiscountRate = 30,
				discountMaleFee = 9000,
				discountFemaleFee = 7000,
			).map { product: GatheringProductEntity -> IntegrationUtil.persist(product) }
			// 얼리버드가 유효한 일정이라 응답 productId는 EARLY_BIRD 상품 id다(실결제가와 같은 행).
			val maleEarlyBirdProductId: Long = products.first { product: GatheringProductEntity ->
				product.gender == Gender.MALE && product.type == GatheringProductType.EARLY_BIRD
			}.id!!
			val femaleEarlyBirdProductId: Long = products.first { product: GatheringProductEntity ->
				product.gender == Gender.FEMALE && product.type == GatheringProductType.EARLY_BIRD
			}.id!!

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
				// 첫 일정(2999-01-01) 남성 아이템 — 얼리버드가 남아있어(remaining 5) 정상가·할인가(=얼리버드가), isEarlyBird=true.
				// 얼리버드가 = 정상가 × (100 - 할인율30) / 100 → 남성 10000×0.7=7000.
				body("data.schedules[0].gender", "MALE")
				body("data.schedules[0].genderDescription", "남성")
				body("data.schedules[0].startAt", "2999-01-01T18:00:00")
				body("data.schedules[0].status", "SCHEDULED")
				body("data.schedules[0].statusDescription", "예정")
				body("data.schedules[0].fee", 10000)
				body("data.schedules[0].discountFee", 7000)
				body("data.schedules[0].isEarlyBird", true)
				body("data.schedules[0].productId", maleEarlyBirdProductId.toInt())
				// 같은 일정의 여성 아이템 — 여성 참가비. 얼리버드가 = 8000×0.7=5600.
				body("data.schedules[1].gender", "FEMALE")
				body("data.schedules[1].fee", 8000)
				body("data.schedules[1].discountFee", 5600)
				body("data.schedules[1].isEarlyBird", true)
				body("data.schedules[1].productId", femaleEarlyBirdProductId.toInt())
				// 두 번째 일정(2999-06-01, 특가 없음) 남성 아이템 — 얼리버드가 없으니 정상가만.
				body("data.schedules[2].gender", "MALE")
				body("data.schedules[2].status", "COMPLETED")
				body("data.schedules[2].statusDescription", "종료")
				body("data.schedules[2].fee", 10000)
				body("data.schedules[2].discountFee", null)
				body("data.schedules[2].isEarlyBird", false)
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
			val scheduleId: Long = IntegrationUtil.persist(
				GatheringScheduleEntityFixture.create(
					gatheringId = id,
					startAt = LocalDateTime.of(2999, 1, 1, 18, 0, 0),
				),
			).id!!
			GatheringProductEntityFixture.tierSet(gatheringId = id, scheduleId = scheduleId, maleFee = 10000, femaleFee = 8000)
				.forEach { product: GatheringProductEntity -> IntegrationUtil.persist(product) }

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

		it("얼리버드가 모두 소진되면 얼리버드가는 null이고 정상가·할인가를 내려준다") {
			val id: Long = IntegrationUtil.persist(
				GatheringEntityFixture.create(title = "얼리버드 소진", status = GatheringStatus.RECRUITING),
			).id!!
			val scheduleId: Long = IntegrationUtil.persist(
				GatheringScheduleEntityFixture.create(
					gatheringId = id,
					startAt = LocalDateTime.of(2999, 1, 1, 18, 0, 0),
					earlyBirdCapacity = 5,
					earlyBirdRemaining = 0,
				),
			).id!!
			GatheringProductEntityFixture.tierSet(
				gatheringId = id,
				scheduleId = scheduleId,
				maleFee = 10000,
				femaleFee = 8000,
				earlyBirdDiscountRate = 30,
				discountMaleFee = 9000,
				discountFemaleFee = 7000,
			).forEach { product: GatheringProductEntity -> IntegrationUtil.persist(product) }

			get("/offline/v1/gatherings/$id") { } expect {
				status(200)
				// 남성 아이템 — 얼리버드 소진 → 정상가·일반 할인가 노출, isEarlyBird=false.
				body("data.schedules[0].gender", "MALE")
				body("data.schedules[0].fee", 10000)
				body("data.schedules[0].discountFee", 9000)
				body("data.schedules[0].isEarlyBird", false)
			}
		}

		it("얼리버드 남은 개수가 null이면(티어 없음) 할인율이 있어도 discountFee는 내려가지 않는다") {
			val id: Long = IntegrationUtil.persist(
				GatheringEntityFixture.create(title = "얼리버드 티어 없음", status = GatheringStatus.RECRUITING),
			).id!!
			val scheduleId: Long = IntegrationUtil.persist(
				GatheringScheduleEntityFixture.create(
					gatheringId = id,
					startAt = LocalDateTime.of(2999, 1, 1, 18, 0, 0),
					// earlyBirdCapacity 미설정 → earlyBirdRemaining null(얼리버드 티어 없음). products에 EARLY_BIRD 행이 있어도
					// view가 earlyBirdRemaining null을 먼저 보고 null을 반환하므로 동작은 유지된다.
				),
			).id!!
			GatheringProductEntityFixture.tierSet(
				gatheringId = id,
				scheduleId = scheduleId,
				maleFee = 10000,
				femaleFee = 8000,
				earlyBirdDiscountRate = 30,
			).forEach { product: GatheringProductEntity -> IntegrationUtil.persist(product) }

			get("/offline/v1/gatherings/$id") { } expect {
				status(200)
				// 얼리버드 티어가 없으므로(remaining null) 정상가만, discountFee는 null, isEarlyBird=false.
				body("data.schedules[0].gender", "MALE")
				body("data.schedules[0].fee", 10000)
				body("data.schedules[0].discountFee", null)
				body("data.schedules[0].isEarlyBird", false)
			}
		}

		it("해당 성별 정원이 소진되면 status가 소진됨(SOLD_OUT)으로 내려간다") {
			val id: Long = IntegrationUtil.persist(
				GatheringEntityFixture.create(title = "정원 소진", status = GatheringStatus.RECRUITING),
			).id!!
			val scheduleId: Long = IntegrationUtil.persist(
				GatheringScheduleEntityFixture.create(
					gatheringId = id,
					startAt = LocalDateTime.of(2999, 1, 1, 18, 0, 0),
					maleCapacity = 4,
					femaleCapacity = 4,
					// 남성 정원만 소진, 여성은 남아있음.
					maleRemaining = 0,
					femaleRemaining = 4,
				),
			).id!!
			GatheringProductEntityFixture.tierSet(gatheringId = id, scheduleId = scheduleId)
				.forEach { product: GatheringProductEntity -> IntegrationUtil.persist(product) }

			get("/offline/v1/gatherings/$id") { } expect {
				status(200)
				// 남성 아이템 — 정원 소진 → 소진됨.
				body("data.schedules[0].gender", "MALE")
				body("data.schedules[0].status", "SOLD_OUT")
				body("data.schedules[0].statusDescription", "소진됨")
				// 여성 아이템 — 정원 남음 → 예정.
				body("data.schedules[1].gender", "FEMALE")
				body("data.schedules[1].status", "SCHEDULED")
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

		it("일정별 참가자 로스터를 내려준다 — JOINED는 프로필·나이 포함, PENDING은 프로필 없이, 거절·취소는 제외") {
			val id: Long = IntegrationUtil.persist(
				GatheringEntityFixture.create(title = "참가자 있는 모임", status = GatheringStatus.RECRUITING),
			).id!!
			val scheduleId: Long = IntegrationUtil.persist(
				GatheringScheduleEntityFixture.create(gatheringId = id, startAt = LocalDateTime.of(2999, 1, 1, 18, 0, 0)),
			).id!!
			GatheringProductEntityFixture.tierSet(gatheringId = id, scheduleId = scheduleId, maleFee = 10000, femaleFee = 8000)
				.forEach { product: GatheringProductEntity -> IntegrationUtil.persist(product) }

			// 참가(JOINED, 남) — 프로필 노출 대상. 먼저 저장해 로스터 첫 항목이 되게 한다(member.id asc 정렬).
			// 프로필 데이터는 gathering_profile에서 온다(멤버 인증 승인으로 생성).
			IntegrationUtil.persist(
				GatheringProfileEntityFixture.create(
					userId = 8101L,
					jobCategory = "IT·개발직",
					jobDetail = "오늘의 소개 백엔드 개발자",
					birthday = LocalDate.of(1996, 1, 1),
					height = 175,
					profileImageCode = "img_01",
				),
			)
			IntegrationUtil.persist(
				GatheringMemberEntityFixture.create(gatheringId = id, scheduleId = scheduleId, userId = 8101L, gender = Gender.MALE, status = GatheringMemberStatus.JOINED),
			)
			// 승인대기(PENDING, 여) — gathering_profile이 있어도 프로필은 비워 내려간다.
			IntegrationUtil.persist(
				GatheringProfileEntityFixture.create(userId = 8102L, jobCategory = "공무원", jobDetail = "행정직 7급"),
			)
			IntegrationUtil.persist(
				GatheringMemberEntityFixture.create(gatheringId = id, scheduleId = scheduleId, userId = 8102L, gender = Gender.FEMALE, status = GatheringMemberStatus.PENDING),
			)
			// 거절·취소 — 로스터에서 제외.
			IntegrationUtil.persist(
				GatheringMemberEntityFixture.create(gatheringId = id, scheduleId = scheduleId, userId = 8103L, gender = Gender.MALE, status = GatheringMemberStatus.REJECTED),
			)
			IntegrationUtil.persist(
				GatheringMemberEntityFixture.create(gatheringId = id, scheduleId = scheduleId, userId = 8104L, gender = Gender.FEMALE, status = GatheringMemberStatus.CANCELED),
			)

			// 비로그인 → 남/녀 두 아이템, 로스터는 성별과 무관한 전체(중복)라 두 아이템 모두 male 1·female 1.
			get("/offline/v1/gatherings/$id") { } expect {
				status(200)
				body("data.schedules", hasSize<Any>(2))
				body("data.schedules[0].participants.male", hasSize<Any>(1))
				body("data.schedules[0].participants.female", hasSize<Any>(1))
				body("data.schedules[1].participants.male", hasSize<Any>(1))
				body("data.schedules[1].participants.female", hasSize<Any>(1))
				// JOINED(남) — gathering_profile 프로필(직종·직장상세·나이·키) 노출, 성별은 그룹(male)이 표현.
				body("data.schedules[0].participants.male[0].status", "JOINED")
				body("data.schedules[0].participants.male[0].userId", 8101)
				body("data.schedules[0].participants.male[0].jobCategory", "IT·개발직")
				body("data.schedules[0].participants.male[0].jobDetail", "오늘의 소개 백엔드 개발자")
				body("data.schedules[0].participants.male[0].age", notNullValue())
				body("data.schedules[0].participants.male[0].height", 175)
				body("data.schedules[0].participants.male[0].profileImageCode", "img_01")
				// PENDING(여) — 상태만, gathering_profile이 있어도 프로필은 비운다.
				body("data.schedules[0].participants.female[0].status", "PENDING")
				body("data.schedules[0].participants.female[0].userId", null)
				body("data.schedules[0].participants.female[0].jobCategory", null)
				body("data.schedules[0].participants.female[0].jobDetail", null)
				body("data.schedules[0].participants.female[0].age", null)
				body("data.schedules[0].participants.female[0].height", null)
				body("data.schedules[0].participants.female[0].profileImageCode", null)
			}
		}
	}

	afterTest {
		IntegrationUtil.deleteAll(QGatheringMemberEntity.gatheringMemberEntity)
		IntegrationUtil.deleteAll(QGatheringProfileEntity.gatheringProfileEntity)
		IntegrationUtil.deleteAll(QGatheringProductEntity.gatheringProductEntity)
		IntegrationUtil.deleteAll(QGatheringScheduleEntity.gatheringScheduleEntity)
		IntegrationUtil.deleteAll(QGatheringEntity.gatheringEntity)
		IntegrationUtil.deleteAll(QUserDetailEntity.userDetailEntity)
	}
})
