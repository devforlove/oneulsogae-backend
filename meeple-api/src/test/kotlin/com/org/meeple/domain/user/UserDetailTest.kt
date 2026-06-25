package com.org.meeple.domain.user

import com.org.meeple.common.user.BodyType
import com.org.meeple.common.user.DrinkingStatus
import com.org.meeple.common.user.Gender
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.common.user.Religion
import com.org.meeple.common.user.SmokingStatus
import com.org.meeple.common.user.UserStatus
import com.org.meeple.core.common.error.BusinessException
import com.org.meeple.core.common.event.MatchProfileSnapshot
import com.org.meeple.core.fixture.UserDetailFixture
import com.org.meeple.core.user.UserErrorCode
import com.org.meeple.core.user.command.domain.UserDetail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
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
				companyEmail = "user@company.com",
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
					companyEmail = "user@company.com",
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
					companyEmail = "user@company.com",
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
					companyEmail = "a@b.com",
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
				companyEmail = "a@b.com",
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
				companyEmail = "a@b.com",
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
					companyEmail = "a@b.com",
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
					companyEmail = "a@b.com",
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
					companyEmail = "a@b.com",
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
			)
		}

		it("정식 가입이 아니면(미완성 단계) null을 반환한다") {
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
