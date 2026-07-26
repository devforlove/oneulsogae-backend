package com.org.oneulsogae.domain.mission

import com.org.oneulsogae.common.mission.MissionType
import com.org.oneulsogae.core.mission.application.evaluator.VerifyUniversityMissionEvaluator
import com.org.oneulsogae.core.user.query.dto.UserDetailView
import com.org.oneulsogae.core.user.query.service.port.`in`.GetUserDetailUseCase
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [VerifyUniversityMissionEvaluator] 유닛 테스트.
 * 학교명 보유 여부로 판정하는지와 지원 유형을 검증한다.
 */
class VerifyUniversityMissionEvaluatorTest : DescribeSpec({

	describe("isEligible") {
		it("학교명이 채워져 있으면 자격이 있다") {
			val evaluator = VerifyUniversityMissionEvaluator(
				FakeUserDetailPort(userDetailViewOf(universityName = "서울대학교")),
			)

			evaluator.isEligible(1L) shouldBe true
		}

		it("학교명이 null이면 자격이 없다") {
			val evaluator = VerifyUniversityMissionEvaluator(FakeUserDetailPort(userDetailViewOf(universityName = null)))

			evaluator.isEligible(1L) shouldBe false
		}

		it("학교명이 공백뿐이면 자격이 없다") {
			val evaluator = VerifyUniversityMissionEvaluator(FakeUserDetailPort(userDetailViewOf(universityName = "   ")))

			evaluator.isEligible(1L) shouldBe false
		}

		it("프로필이 없으면 자격이 없다") {
			val evaluator = VerifyUniversityMissionEvaluator(FakeUserDetailPort(null))

			evaluator.isEligible(1L) shouldBe false
		}
	}

	describe("supports") {
		it("VERIFY_UNIVERSITY만 지원한다") {
			val evaluator = VerifyUniversityMissionEvaluator(FakeUserDetailPort(null))

			evaluator.supports(MissionType.VERIFY_UNIVERSITY) shouldBe true
			evaluator.supports(MissionType.WRITE_INTRODUCTION) shouldBe false
		}
	}
})

/** 학교명(universityName)만 바꿔가며 검증하기 위한 [UserDetailView] 생성 헬퍼. 나머지 필드는 중립값. */
private fun userDetailViewOf(universityName: String?): UserDetailView = UserDetailView(
	id = 1L,
	userId = 1L,
	nickname = null,
	profileImageCode = null,
	birthday = null,
	height = null,
	gender = null,
	phoneNumber = null,
	job = null,
	regionId = null,
	activityArea = null,
	introduction = null,
	traits = emptyList(),
	interests = emptyList(),
	companyEmail = null,
	companyName = null,
	universityEmail = null,
	universityName = universityName,
	secondaryEmail = null,
	maritalStatus = null,
	smokingStatus = null,
	religion = null,
	drinkingStatus = null,
	bodyType = null,
	refuseSameCompanyIntro = false,
)

/** [GetUserDetailUseCase]의 수동 페이크. 주입된 detail을 그대로 반환한다. */
private class FakeUserDetailPort(private val detail: UserDetailView?) : GetUserDetailUseCase {
	override fun getByUserId(userId: Long): UserDetailView = detail!!
	override fun findByUserId(userId: Long): UserDetailView? = detail
}
