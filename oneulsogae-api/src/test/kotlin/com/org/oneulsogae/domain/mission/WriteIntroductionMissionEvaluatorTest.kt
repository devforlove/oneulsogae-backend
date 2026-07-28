package com.org.oneulsogae.domain.mission

import com.org.oneulsogae.common.mission.MissionType
import com.org.oneulsogae.core.mission.application.evaluator.WriteIntroductionMissionEvaluator
import com.org.oneulsogae.core.user.query.dto.UserDetailView
import com.org.oneulsogae.core.user.query.service.port.`in`.GetUserDetailUseCase
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [WriteIntroductionMissionEvaluator] 유닛 테스트.
 * 자기소개 길이(앞뒤 공백 제외) 100자 임계값과 지원 유형을 검증한다.
 */
class WriteIntroductionMissionEvaluatorTest : DescribeSpec({

	describe("isEligible") {
		it("소개가 100자 이상이면 자격이 있다") {
			val evaluator = WriteIntroductionMissionEvaluator(
				FakeGetUserDetailUseCase(userDetailViewOf(introduction = "가".repeat(100))),
			)

			evaluator.isEligible(1L) shouldBe true
		}

		it("소개가 99자면 자격이 없다") {
			val evaluator = WriteIntroductionMissionEvaluator(
				FakeGetUserDetailUseCase(userDetailViewOf(introduction = "가".repeat(99))),
			)

			evaluator.isEligible(1L) shouldBe false
		}

		it("앞뒤 공백을 뺀 길이로 판정한다 (공백 패딩은 자격 없음)") {
			val evaluator = WriteIntroductionMissionEvaluator(
				FakeGetUserDetailUseCase(userDetailViewOf(introduction = "  " + "가".repeat(50) + "   ")),
			)

			evaluator.isEligible(1L) shouldBe false
		}

		it("프로필이 없거나 소개가 null이면 자격이 없다") {
			val evaluator = WriteIntroductionMissionEvaluator(FakeGetUserDetailUseCase(null))

			evaluator.isEligible(1L) shouldBe false
		}
	}

	describe("supports") {
		it("WRITE_INTRODUCTION만 지원한다") {
			val evaluator = WriteIntroductionMissionEvaluator(FakeGetUserDetailUseCase(null))

			evaluator.supports(MissionType.WRITE_INTRODUCTION) shouldBe true
		}
	}
})

/** 소개(introduction)만 바꿔가며 검증하기 위한 [UserDetailView] 생성 헬퍼. 나머지 필드는 중립값. */
private fun userDetailViewOf(introduction: String?): UserDetailView = UserDetailView(
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
	introduction = introduction,
	traits = emptyList(),
	interests = emptyList(),
	companyEmail = null,
	companyName = null,
	universityEmail = null,
	universityName = null,
	secondaryEmail = null,
	maritalStatus = null,
	smokingStatus = null,
	religion = null,
	drinkingStatus = null,
	bodyType = null,
	mbti = null,
	refuseSameCompanyIntro = false,
)

/** [GetUserDetailUseCase]의 수동 페이크. 주입된 detail을 그대로 반환한다. */
private class FakeGetUserDetailUseCase(private val detail: UserDetailView?) : GetUserDetailUseCase {
	override fun getByUserId(userId: Long): UserDetailView = detail!!
	override fun findByUserId(userId: Long): UserDetailView? = detail
}
