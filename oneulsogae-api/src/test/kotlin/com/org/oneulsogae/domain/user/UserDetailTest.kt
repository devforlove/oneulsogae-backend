package com.org.oneulsogae.domain.user

import com.org.oneulsogae.common.user.BodyType
import com.org.oneulsogae.common.user.DrinkingStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.common.user.MaritalStatus
import com.org.oneulsogae.common.user.Religion
import com.org.oneulsogae.common.user.SmokingStatus
import com.org.oneulsogae.common.user.UserStatus
import com.org.oneulsogae.core.common.error.BusinessException
import com.org.oneulsogae.core.common.event.MatchProfileSnapshot
import com.org.oneulsogae.core.fixture.UserDetailFixture
import com.org.oneulsogae.core.user.UserErrorCode
import com.org.oneulsogae.core.user.command.domain.UserDetail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [UserDetail] 도메인 유닛 테스트.
 * 정식 가입(ACTIVE)으로 이어지는 프로필 입력/편집이 매칭 풀 필수값(성별·활동지역)을 강제하는지 검증한다.
 * 이 불변식이 매칭 풀 조회의 성별·지역 null 필터 제거를 떠받친다.
 */
class UserDetailTest : DescribeSpec({

	val today: LocalDate = LocalDate.of(2026, 6, 22)
	val base: UserDetail = UserDetail.create(userId = 1L)

	describe("initProfile") {
		it("성별과 활동지역이 주어지면 성별·활동지역이 채워진 프로필을 만든다") {
			val updated: UserDetail = base.initProfile(
				nickname = "민수",
				birthday = LocalDate.of(1995, 1, 1),
				height = 175,
				gender = Gender.MALE,
				phoneNumber = null,
				job = null,
				regionId = 1L,
				introduction = null,
				traits = emptyList(),
				interests = emptyList(),
				maritalStatus = null,
				smokingStatus = null,
				religion = null,
				drinkingStatus = null,
				bodyType = null,
				today = today,
			)

			updated.gender shouldBe Gender.MALE
			updated.regionId shouldBe 1L
		}

		it("성별이 없으면 GENDER_REQUIRED를 던진다") {
			val ex: BusinessException = shouldThrow {
				base.initProfile(
					nickname = "민수",
					birthday = LocalDate.of(1995, 1, 1),
					height = 175,
					gender = null,
					phoneNumber = null,
					job = null,
					regionId = 1L,
					introduction = null,
					traits = emptyList(),
					interests = emptyList(),
					maritalStatus = null,
					smokingStatus = null,
					religion = null,
					drinkingStatus = null,
					bodyType = null,
					today = today,
				)
			}

			ex.errorCode shouldBe UserErrorCode.GENDER_REQUIRED
		}

		it("활동지역(regionId)이 없으면 REGION_NOT_RESOLVED를 던진다") {
			val ex: BusinessException = shouldThrow {
				base.initProfile(
					nickname = "민수",
					birthday = LocalDate.of(1995, 1, 1),
					height = 175,
					gender = Gender.MALE,
					phoneNumber = null,
					job = null,
					regionId = null,
					introduction = null,
					traits = emptyList(),
					interests = emptyList(),
					maritalStatus = null,
					smokingStatus = null,
					religion = null,
					drinkingStatus = null,
					bodyType = null,
					today = today,
				)
			}

			ex.errorCode shouldBe UserErrorCode.REGION_NOT_RESOLVED
		}

		it("만 19세 미만이면 INVALID_BIRTHDAY를 던진다") {
			// 2007-06-23 생 → today(2026-06-22) 기준 만 18세
			val ex: BusinessException = shouldThrow {
				base.initProfile(
					nickname = "닉",
					birthday = LocalDate.of(2007, 6, 23),
					height = 175,
					gender = Gender.MALE,
					phoneNumber = "010-0000-0000",
					job = "개발자",
					regionId = 1L,
					introduction = "소개",
					traits = listOf("성실함"),
					interests = listOf("영화"),
					maritalStatus = MaritalStatus.SINGLE,
					smokingStatus = SmokingStatus.NON_SMOKER,
					religion = Religion.NONE,
					drinkingStatus = DrinkingStatus.SOMETIMES,
					bodyType = BodyType.MALE_NORMAL,
					today = today,
				)
			}

			ex.errorCode shouldBe UserErrorCode.INVALID_BIRTHDAY
		}

		it("만 19세 경계(2007-06-22 생)는 통과한다") {
			// 2007-06-22 생 → today(2026-06-22) 기준 정확히 만 19세
			val updated: UserDetail = base.initProfile(
				nickname = "닉",
				birthday = LocalDate.of(2007, 6, 22),
				height = 175,
				gender = Gender.MALE,
				phoneNumber = "010-0000-0000",
				job = "개발자",
				regionId = 1L,
				introduction = "소개",
				traits = listOf("성실함"),
				interests = listOf("영화"),
				maritalStatus = MaritalStatus.SINGLE,
				smokingStatus = SmokingStatus.NON_SMOKER,
				religion = Religion.NONE,
				drinkingStatus = DrinkingStatus.SOMETIMES,
				bodyType = BodyType.MALE_NORMAL,
				today = today,
			)

			updated.age(today) shouldBe 19
		}

		it("만 100세 경계(1926-06-22 생)는 통과한다") {
			// 1926-06-22 생 → today(2026-06-22) 기준 정확히 만 100세
			val updated: UserDetail = base.initProfile(
				nickname = "닉",
				birthday = LocalDate.of(1926, 6, 22),
				height = 165,
				gender = Gender.FEMALE,
				phoneNumber = "010-0000-0000",
				job = "은퇴",
				regionId = 1L,
				introduction = "소개",
				traits = listOf("성실함"),
				interests = listOf("영화"),
				maritalStatus = MaritalStatus.SINGLE,
				smokingStatus = SmokingStatus.NON_SMOKER,
				religion = Religion.NONE,
				drinkingStatus = DrinkingStatus.SOMETIMES,
				bodyType = BodyType.FEMALE_NORMAL,
				today = today,
			)

			updated.age(today) shouldBe 100
		}

		it("만 101세 이상이면 INVALID_BIRTHDAY를 던진다") {
			// 1925-06-21 생 → today(2026-06-22) 기준 만 101세
			val ex: BusinessException = shouldThrow {
				base.initProfile(
					nickname = "닉",
					birthday = LocalDate.of(1925, 6, 21),
					height = 165,
					gender = Gender.FEMALE,
					phoneNumber = "010-0000-0000",
					job = "은퇴",
					regionId = 1L,
					introduction = "소개",
					traits = listOf("성실함"),
					interests = listOf("영화"),
					maritalStatus = MaritalStatus.SINGLE,
					smokingStatus = SmokingStatus.NON_SMOKER,
					religion = Religion.NONE,
					drinkingStatus = DrinkingStatus.SOMETIMES,
					bodyType = BodyType.FEMALE_NORMAL,
					today = today,
				)
			}

			ex.errorCode shouldBe UserErrorCode.INVALID_BIRTHDAY
		}

		it("미래 날짜 생년월일이면 INVALID_BIRTHDAY를 던진다") {
			val ex: BusinessException = shouldThrow {
				base.initProfile(
					nickname = "닉",
					birthday = LocalDate.of(2030, 1, 1),
					height = 175,
					gender = Gender.MALE,
					phoneNumber = "010-0000-0000",
					job = "개발자",
					regionId = 1L,
					introduction = "소개",
					traits = listOf("성실함"),
					interests = listOf("영화"),
					maritalStatus = MaritalStatus.SINGLE,
					smokingStatus = SmokingStatus.NON_SMOKER,
					religion = Religion.NONE,
					drinkingStatus = DrinkingStatus.SOMETIMES,
					bodyType = BodyType.MALE_NORMAL,
					today = today,
				)
			}

			ex.errorCode shouldBe UserErrorCode.INVALID_BIRTHDAY
		}

		it("생년월일이 없으면 BIRTHDAY_REQUIRED를 던진다") {
			val ex: BusinessException = shouldThrow {
				base.initProfile(
					nickname = "닉",
					birthday = null,
					height = 175,
					gender = Gender.MALE,
					phoneNumber = "010-0000-0000",
					job = "개발자",
					regionId = 1L,
					introduction = "소개",
					traits = listOf("성실함"),
					interests = listOf("영화"),
					maritalStatus = MaritalStatus.SINGLE,
					smokingStatus = SmokingStatus.NON_SMOKER,
					religion = Religion.NONE,
					drinkingStatus = DrinkingStatus.SOMETIMES,
					bodyType = BodyType.MALE_NORMAL,
					today = today,
				)
			}

			ex.errorCode shouldBe UserErrorCode.BIRTHDAY_REQUIRED
		}
	}

	describe("age") {
		it("생년월일이 없으면 null을 반환한다") {
			UserDetailFixture.create(birthday = null).age(today) shouldBe null
		}

		it("생년월일이 있으면 만 나이를 반환한다") {
			// 1995-01-01 생 → today(2026-06-22) 기준 만 31세
			UserDetailFixture.create(birthday = LocalDate.of(1995, 1, 1)).age(today) shouldBe 31
		}
	}

	describe("editProfile") {
		// 이미 가입을 마친(성별·활동지역이 채워진) 프로필을 픽스처로 만든다. (initProfile의 base는 온보딩 전 빈 프로필이라 팩토리를 그대로 쓴다)
		val registered: UserDetail = UserDetailFixture.create(gender = Gender.FEMALE)

		it("활동지역을 편집하면 성별은 보존되고 활동지역이 갱신된다") {
			val updated: UserDetail = registered.editProfile(
				nickname = "지은",
				profileImageCode = "3",
				job = null,
				regionId = 2L,
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
			updated.regionId shouldBe 2L
		}

		it("활동지역(regionId)이 없으면 REGION_NOT_RESOLVED를 던진다") {
			val ex: BusinessException = shouldThrow {
				registered.editProfile(
					nickname = "지은",
					profileImageCode = "3",
					job = null,
					regionId = null,
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

	describe("changeSecondaryEmail") {
		val registered: UserDetail = UserDetailFixture.create(gender = Gender.FEMALE)

		it("보조 이메일을 설정하면 해당 값이 채워지고 다른 필드는 보존된다") {
			val updated: UserDetail = registered.changeSecondaryEmail("marketing@user.com")

			updated.secondaryEmail shouldBe "marketing@user.com"
			updated.companyEmail shouldBe registered.companyEmail // 다른 이메일은 보존
			updated.gender shouldBe Gender.FEMALE
		}

		it("이미 설정된 보조 이메일을 다른 값으로 교체한다") {
			val updated: UserDetail = registered
				.changeSecondaryEmail("old@user.com")
				.changeSecondaryEmail("new@user.com")

			updated.secondaryEmail shouldBe "new@user.com"
		}

		it("null을 주면 보조 이메일이 해제된다") {
			val updated: UserDetail = registered
				.changeSecondaryEmail("marketing@user.com")
				.changeSecondaryEmail(null)

			updated.secondaryEmail.shouldBeNull()
		}

		it("공백 문자열을 주면 해제로 간주해 null로 정규화한다") {
			val updated: UserDetail = registered
				.changeSecondaryEmail("marketing@user.com")
				.changeSecondaryEmail("   ")

			updated.secondaryEmail.shouldBeNull()
		}
	}

	describe("matchProfileSnapshotOrNull") {
		val loginAt: LocalDateTime = LocalDateTime.of(2026, 6, 19, 10, 0)

		it("정식 가입 + 필수 필드가 모두 채워지면 매칭 스냅샷을 만든다") {
			val birthday: LocalDate = LocalDate.of(1999, 3, 15)
			val complete: UserDetail = UserDetailFixture.create(
				userId = 7L,
				gender = Gender.FEMALE,
				birthday = birthday,
				regionId = 1L,
			)

			val snapshot: MatchProfileSnapshot? = complete.matchProfileSnapshotOrNull(UserStatus.ACTIVE, loginAt)

			snapshot shouldBe MatchProfileSnapshot(
				gender = Gender.FEMALE,
				birthday = birthday,
				regionId = 1L,
				maritalStatus = complete.maritalStatus!!,
				nickname = complete.nickname!!,
				profileImageCode = complete.profileImageCode!!,
				lastLoginAt = loginAt,
				companyName = complete.companyName,
			)
		}

		it("회사명 미확정(COMPANY_NOT_RESOLVED)이어도 회사 이메일 인증을 마쳤으므로 매칭 스냅샷을 만든다") {
			val complete: UserDetail = UserDetailFixture.create(
				userId = 7L,
				gender = Gender.FEMALE,
				birthday = LocalDate.of(1999, 3, 15),
				regionId = 1L,
			)

			complete.matchProfileSnapshotOrNull(UserStatus.COMPANY_NOT_RESOLVED, loginAt).shouldNotBeNull()
		}

		it("아직 회사 이메일 인증 전(미완성 단계)이면 null을 반환한다") {
			val complete: UserDetail = UserDetailFixture.create()

			complete.matchProfileSnapshotOrNull(UserStatus.EMAIL_VERIFICATION_PENDING, loginAt).shouldBeNull()
		}

		it("필수 필드(활동지역)가 비어 있으면 null을 반환한다") {
			val incomplete: UserDetail = UserDetailFixture.create(regionId = null)

			incomplete.matchProfileSnapshotOrNull(UserStatus.ACTIVE, loginAt).shouldBeNull()
		}

		it("마지막 로그인 시각이 없으면 null을 반환한다") {
			val complete: UserDetail = UserDetailFixture.create()

			complete.matchProfileSnapshotOrNull(UserStatus.ACTIVE, null).shouldBeNull()
		}
	}
})
