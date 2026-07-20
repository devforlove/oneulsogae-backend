package com.org.oneulsogae.api.user

import com.org.oneulsogae.common.integration.AbstractIntegrationSupport
import com.org.oneulsogae.common.integration.expect
import com.org.oneulsogae.common.integration.post
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.common.user.UserStatus
import com.org.oneulsogae.infra.fixture.IntegrationUtil
import com.org.oneulsogae.infra.fixture.RegionEntityFixture
import com.org.oneulsogae.infra.fixture.UserDetailEntityFixture
import com.org.oneulsogae.infra.fixture.UserEntityFixture
import com.org.oneulsogae.infra.user.command.entity.UserDetailEntity
import io.kotest.matchers.shouldBe

/**
 * `POST /users/v1/onboarding/complete` E2E 테스트.
 *
 * 온보딩 입력값(프로필 상세)을 저장하고 정식 가입(ACTIVE)으로 전환하며,
 * 가입 축하 코인 지급·코인 잔액 행 준비 등 완료 부수효과가 함께 처리되는지 검증한다.
 * 공통 정리/조회 헬퍼는 [OnboardingE2ESupport] 파일의 최상위 함수로 둔다.
 */
class CompleteOnboardingE2ETest : AbstractIntegrationSupport({

	describe("POST /users/v1/onboarding/complete") {

		context("온보딩 사용자가 모든 프로필을 채워 완료를 요청하면") {
			it("프로필이 저장되고 정식 가입(ACTIVE)되며 가입 축하 코인이 지급된다 (200)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ONBOARDING),
				).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId))
				val regionId: Long = IntegrationUtil.persist(
					RegionEntityFixture.create(sido = "서울특별시", sigungu = "강남구"),
				).id!!

				post("/users/v1/onboarding/complete") {
					bearer(accessTokenFor(userId))
					jsonBody(fullProfileBody(regionId = regionId))
				} expect {
					status(200)
					body("success", true)
				}

				// 부수효과: ACTIVE 전환 + 프로필(성별) 저장 + 코인 잔액 행 준비 + 가입 축하 코인 지급
				userStatusOf(userId) shouldBe UserStatus.ACTIVE
				userDetailOf(userId).gender shouldBe Gender.MALE
				coinBalanceCountOf(userId) shouldBe 1
				coinBalanceOf(userId) shouldBe 100
			}
		}

		context("프로필(UserDetail)이 아직 없는 신규 가입자가 완료를 요청하면") {
			it("프로필이 새로 생성·저장되고 정식 가입(ACTIVE)된다 (200)") {
				// 가입 직후 상태: User만 있고 UserDetail은 아직 없다. (detail 픽스처를 만들지 않는다)
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ONBOARDING),
				).id!!
				val regionId: Long = IntegrationUtil.persist(
					RegionEntityFixture.create(sido = "서울특별시", sigungu = "강남구"),
				).id!!

				post("/users/v1/onboarding/complete") {
					bearer(accessTokenFor(userId))
					jsonBody(fullProfileBody(regionId = regionId))
				} expect {
					status(200)
					body("success", true)
				}

				userStatusOf(userId) shouldBe UserStatus.ACTIVE
				userDetailOf(userId).gender shouldBe Gender.MALE
				coinBalanceCountOf(userId) shouldBe 1
			}
		}

		context("필수 프로필 필드(닉네임)가 빠지면") {
			it("도메인에 닿기 전 검증 실패로 400을 반환한다") {
				post("/users/v1/onboarding/complete") {
					bearer(accessTokenFor(1L))
					jsonBody(fullProfileBody(regionId = 1L, nickname = null))
				} expect {
					status(400)
					body("success", false)
					body("error.code", "INVALID_REQUEST")
				}
			}
		}

		context("본인인증으로 birthday·gender·phone이 이미 저장된 유저가 그 3필드를 생략해 제출하면") {
			it("기존 인증 저장값이 유지되고 정식 가입(ACTIVE)된다 (200)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ONBOARDING),
				).id!!
				// 인증(confirm)이 심어둔 신뢰값을 흉내: gender=FEMALE, birthday=1996-01-01 보유
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(
						userId = userId,
						gender = Gender.FEMALE,
						birthday = java.time.LocalDate.of(1996, 1, 1),
					),
				)
				val regionId: Long = IntegrationUtil.persist(
					RegionEntityFixture.create(sido = "서울특별시", sigungu = "강남구"),
				).id!!

				post("/users/v1/onboarding/complete") {
					bearer(accessTokenFor(userId))
					jsonBody(profileBodyWithoutIdentity(regionId = regionId))
				} expect {
					status(200)
					body("success", true)
				}

				val detail: UserDetailEntity = userDetailOf(userId)
				detail.gender shouldBe Gender.FEMALE
				detail.birthday shouldBe java.time.LocalDate.of(1996, 1, 1)
				userStatusOf(userId) shouldBe UserStatus.ACTIVE
			}
		}
	}

	afterTest {
		cleanupOnboarding()
	}
})

/** 모든 필수 필드를 채운 온보딩 프로필 입력 JSON. (nickname=null·gender=null로 필수 검증 실패 케이스를 만들 수 있다) */
private fun fullProfileBody(
	regionId: Long,
	nickname: String? = "테스트유저",
	gender: Gender? = Gender.MALE,
): String {
	val nicknameJson: String = nickname?.let { "\"$it\"" } ?: "null"
	val genderJson: String = gender?.let { "\"${it.name}\"" } ?: "null"
	return """
		{
		  "nickname": $nicknameJson,
		  "birthday": "1995-01-01",
		  "height": 175,
		  "gender": $genderJson,
		  "phoneNumber": "010-1234-5678",
		  "job": "개발자",
		  "regionId": $regionId,
		  "introduction": "안녕하세요 잘 부탁드립니다.",
		  "traits": ["성실함"],
		  "interests": ["영화"],
		  "maritalStatus": "SINGLE",
		  "smokingStatus": "NON_SMOKER",
		  "religion": "NONE",
		  "drinkingStatus": "SOMETIMES",
		  "bodyType": "MALE_NORMAL"
		}
	""".trimIndent()
}

private fun profileBodyWithoutIdentity(regionId: Long): String =
	"""
	{
	  "nickname": "테스트유저",
	  "height": 175,
	  "job": "개발자",
	  "regionId": $regionId,
	  "introduction": "안녕하세요 잘 부탁드립니다.",
	  "traits": ["성실함"],
	  "interests": ["영화"],
	  "maritalStatus": "SINGLE",
	  "smokingStatus": "NON_SMOKER",
	  "religion": "NONE",
	  "drinkingStatus": "SOMETIMES",
	  "bodyType": "MALE_NORMAL"
	}
	""".trimIndent()
