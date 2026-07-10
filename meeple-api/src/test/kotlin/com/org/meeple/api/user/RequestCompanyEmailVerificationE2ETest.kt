package com.org.meeple.api.user

import com.org.meeple.common.integration.AbstractIntegrationSupport
import com.org.meeple.common.integration.expect
import com.org.meeple.common.integration.post
import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.UserStatus
import com.org.meeple.infra.fixture.IntegrationUtil
import com.org.meeple.infra.fixture.RegionEntityFixture
import com.org.meeple.infra.fixture.UserDetailEntityFixture
import com.org.meeple.infra.fixture.UserEntityFixture
import com.org.meeple.infra.user.command.entity.UserDetailEntity
import io.kotest.matchers.shouldBe
import org.hamcrest.Matchers.notNullValue

/**
 * `POST /users/v1/onboarding/company-email/verifications` E2E 테스트.
 *
 * 온보딩 프로필 입력값을 저장하고, 입력한 회사 이메일로 인증번호를 발급하는 경로를 검증한다.
 * 공통 정리/조회 헬퍼는 [OnboardingE2ESupport] 파일의 최상위 함수로 둔다.
 */
class RequestCompanyEmailVerificationE2ETest : AbstractIntegrationSupport({

	describe("POST /users/v1/onboarding/company-email/verifications") {

		context("온보딩 사용자가 모든 프로필을 채워 회사 이메일 인증을 요청하면") {
			it("프로필이 저장되고 인증 단계로 전환되며 인증번호가 발급된다 (200)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ONBOARDING),
				).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId))
				val regionId: Long = IntegrationUtil.persist(
					RegionEntityFixture.create(sido = "서울특별시", sigungu = "강남구"),
				).id!!

				post("/users/v1/onboarding/company-email/verifications") {
					bearer(accessTokenFor(userId))
					jsonBody(fullProfileBody(companyEmail = "user@meeple.com", regionId = regionId))
				} expect {
					status(200)
					body("success", true)
					body("data.companyEmail", "user@meeple.com")
					body("data.expiresAt", notNullValue())
				}

				// 부수효과: 사용자 상태 전환 + 프로필(성별) 저장 + 인증 요청 1건 생성
				userStatusOf(userId) shouldBe UserStatus.EMAIL_VERIFICATION_PENDING
				val detail: UserDetailEntity = userDetailOf(userId)
				detail.gender shouldBe Gender.MALE
				verificationCountOf(userId) shouldBe 1
			}
		}

		context("프로필(UserDetail)이 아직 없는 신규 가입자가 회사 이메일 인증을 요청하면") {
			it("프로필이 새로 생성·저장되고 인증 단계로 전환된다 (200)") {
				// 가입 직후 상태: User만 있고 UserDetail은 아직 없다. (detail 픽스처를 만들지 않는다)
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ONBOARDING),
				).id!!
				val regionId: Long = IntegrationUtil.persist(
					RegionEntityFixture.create(sido = "서울특별시", sigungu = "강남구"),
				).id!!

				post("/users/v1/onboarding/company-email/verifications") {
					bearer(accessTokenFor(userId))
					jsonBody(fullProfileBody(companyEmail = "user@meeple.com", regionId = regionId))
				} expect {
					status(200)
					body("success", true)
					body("data.companyEmail", "user@meeple.com")
				}

				// detail이 없던 신규 가입자라도 프로필이 새로 생성되고 인증 단계로 전환된다.
				userStatusOf(userId) shouldBe UserStatus.EMAIL_VERIFICATION_PENDING
				val detail: UserDetailEntity = userDetailOf(userId)
				detail.gender shouldBe Gender.MALE
				verificationCountOf(userId) shouldBe 1
				// 온보딩 커맨드 경로에서 코인 잔액 행이 준비된다. (조회가 아닌 커맨드가 생성을 담당)
				coinBalanceCountOf(userId) shouldBe 1
			}
		}

		context("다른 사용자가 이미 인증해 쓰고 있는 회사 이메일로 인증을 요청하면") {
			it("부수효과 없이 409(COMPANY_EMAIL_ALREADY_USED)를 반환한다") {
				// 다른 사용자가 이미 해당 회사 이메일을 인증해 프로필에 보유한 상태.
				val otherUserId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(
						providerId = "other-provider-id",
						email = "other@test.com",
						status = UserStatus.ACTIVE,
					),
				).id!!
				IntegrationUtil.persist(
					UserDetailEntityFixture.create(userId = otherUserId, companyEmail = "user@meeple.com"),
				)

				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ONBOARDING),
				).id!!
				IntegrationUtil.persist(UserDetailEntityFixture.create(userId = userId))
				val regionId: Long = IntegrationUtil.persist(
					RegionEntityFixture.create(sido = "서울특별시", sigungu = "강남구"),
				).id!!

				post("/users/v1/onboarding/company-email/verifications") {
					bearer(accessTokenFor(userId))
					jsonBody(fullProfileBody(companyEmail = "user@meeple.com", regionId = regionId))
				} expect {
					status(409)
					body("success", false)
					body("error.code", "USER-017")
				}

				// 중복으로 막혔으므로 상태 전환·인증번호 발급 등 부수효과가 없어야 한다.
				userStatusOf(userId) shouldBe UserStatus.ONBOARDING
				verificationCountOf(userId) shouldBe 0
			}
		}

		context("필수 프로필 필드(닉네임)가 빠지면") {
			it("도메인에 닿기 전 검증 실패로 400을 반환한다") {
				post("/users/v1/onboarding/company-email/verifications") {
					bearer(accessTokenFor(1L))
					jsonBody(fullProfileBody(companyEmail = "user@meeple.com", regionId = 1L, nickname = null))
				} expect {
					status(400)
					body("success", false)
					body("error.code", "INVALID_REQUEST")
				}
			}
		}

		context("본인인증으로 birthday·gender·phone이 이미 저장된 유저가 그 3필드를 생략해 제출하면") {
			it("기존 인증 저장값이 유지되고 인증 단계로 전환된다 (200)") {
				val userId: Long = IntegrationUtil.persist(
					UserEntityFixture.create(status = UserStatus.ONBOARDING),
				).id!!
				// 인증(confirm)이 심어둔 신뢰값을 흉내: gender=FEMALE, birthday=1996-01-01, phone 보유
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

				post("/users/v1/onboarding/company-email/verifications") {
					bearer(accessTokenFor(userId))
					jsonBody(profileBodyWithoutIdentity(companyEmail = "user@meeple.com", regionId = regionId))
				} expect {
					status(200)
					body("success", true)
				}

				val detail: UserDetailEntity = userDetailOf(userId)
				detail.gender shouldBe Gender.FEMALE
				detail.birthday shouldBe java.time.LocalDate.of(1996, 1, 1)
			}
		}
	}

	afterTest {
		cleanupOnboarding()
	}
})

/** 모든 필수 필드를 채운 온보딩 프로필 입력 JSON. (nickname=null·gender=null로 필수 검증 실패 케이스를 만들 수 있다) */
private fun fullProfileBody(
	companyEmail: String,
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
		  "companyEmail": "$companyEmail",
		  "maritalStatus": "SINGLE",
		  "smokingStatus": "NON_SMOKER",
		  "religion": "NONE",
		  "drinkingStatus": "SOMETIMES",
		  "bodyType": "MALE_NORMAL"
		}
	""".trimIndent()
}

private fun profileBodyWithoutIdentity(companyEmail: String, regionId: Long): String =
	"""
	{
	  "nickname": "테스트유저",
	  "height": 175,
	  "job": "개발자",
	  "regionId": $regionId,
	  "introduction": "안녕하세요 잘 부탁드립니다.",
	  "traits": ["성실함"],
	  "interests": ["영화"],
	  "companyEmail": "$companyEmail",
	  "maritalStatus": "SINGLE",
	  "smokingStatus": "NON_SMOKER",
	  "religion": "NONE",
	  "drinkingStatus": "SOMETIMES",
	  "bodyType": "MALE_NORMAL"
	}
	""".trimIndent()
