package com.org.meeple.domain.user

import com.org.meeple.common.user.Gender
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.fixture.UserDetailFixture
import com.org.meeple.core.user.UserErrorCode
import com.org.meeple.core.user.command.domain.UserDetail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [UserDetail] 도메인 유닛 테스트.
 * 정식 가입(ACTIVE)으로 이어지는 프로필 입력/편집이 매칭 풀 필수값(성별·활동권역)을 강제하는지 검증한다.
 * 이 불변식이 매칭 풀 조회의 성별·권역 null 필터 제거를 떠받친다.
 */
class UserDetailTest : DescribeSpec({

	val base: UserDetail = UserDetail.create(userId = 1L)

	describe("initProfile") {
		it("성별과 인식 가능한 활동지역이 주어지면 성별·권역이 채워진 프로필을 만든다") {
			val updated: UserDetail = base.initProfile(
				nickname = "민수",
				age = 30,
				height = 175,
				gender = Gender.MALE,
				phoneNumber = null,
				job = null,
				activityArea = "서울특별시 강남구",
				introduction = null,
				traits = emptyList(),
				interests = emptyList(),
				companyEmail = "user@company.com",
				maritalStatus = null,
				smokingStatus = null,
				religion = null,
				drinkingStatus = null,
				bodyType = null,
			)

			updated.gender shouldBe Gender.MALE
			updated.regionCode shouldBe 1 // 서울 -> 권역 1
		}

		it("성별이 없으면 GENDER_REQUIRED를 던진다") {
			val ex: BusinessException = shouldThrow {
				base.initProfile(
					nickname = "민수",
					age = 30,
					height = 175,
					gender = null,
					phoneNumber = null,
					job = null,
					activityArea = "서울특별시 강남구",
					introduction = null,
					traits = emptyList(),
					interests = emptyList(),
					companyEmail = "user@company.com",
					maritalStatus = null,
					smokingStatus = null,
					religion = null,
					drinkingStatus = null,
					bodyType = null,
				)
			}

			ex.errorCode shouldBe UserErrorCode.GENDER_REQUIRED
		}

		it("활동지역을 권역으로 인식하지 못하면 REGION_NOT_RESOLVED를 던진다") {
			val ex: BusinessException = shouldThrow {
				base.initProfile(
					nickname = "민수",
					age = 30,
					height = 175,
					gender = Gender.MALE,
					phoneNumber = null,
					job = null,
					activityArea = "해외 어딘가",
					introduction = null,
					traits = emptyList(),
					interests = emptyList(),
					companyEmail = "user@company.com",
					maritalStatus = null,
					smokingStatus = null,
					religion = null,
					drinkingStatus = null,
					bodyType = null,
				)
			}

			ex.errorCode shouldBe UserErrorCode.REGION_NOT_RESOLVED
		}
	}

	describe("editProfile") {
		// 이미 가입을 마친(성별·권역이 채워진) 프로필을 픽스처로 만든다. (initProfile의 base는 온보딩 전 빈 프로필이라 팩토리를 그대로 쓴다)
		val registered: UserDetail = UserDetailFixture.create(gender = Gender.FEMALE)

		it("인식 가능한 활동지역으로 편집하면 성별은 보존되고 권역이 갱신된다") {
			val updated: UserDetail = registered.editProfile(
				nickname = "지은",
				profileImageCode = "3",
				job = null,
				activityArea = "부산광역시 해운대구",
				introduction = null,
				traits = emptyList(),
				interests = emptyList(),
				maritalStatus = null,
				smokingStatus = null,
				religion = null,
				drinkingStatus = null,
				bodyType = null,
			)

			updated.gender shouldBe Gender.FEMALE // 편집에서 성별은 보존
			updated.regionCode shouldBe 2 // 부산 -> 권역 2
		}

		it("활동지역을 권역으로 인식하지 못하면 REGION_NOT_RESOLVED를 던진다") {
			val ex: BusinessException = shouldThrow {
				registered.editProfile(
					nickname = "지은",
					profileImageCode = "3",
					job = null,
					activityArea = "",
					introduction = null,
					traits = emptyList(),
					interests = emptyList(),
					maritalStatus = null,
					smokingStatus = null,
					religion = null,
					drinkingStatus = null,
					bodyType = null,
				)
			}

			ex.errorCode shouldBe UserErrorCode.REGION_NOT_RESOLVED
		}
	}
})
