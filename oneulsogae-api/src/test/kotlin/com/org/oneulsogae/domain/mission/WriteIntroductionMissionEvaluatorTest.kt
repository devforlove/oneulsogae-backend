package com.org.oneulsogae.domain.mission

import com.org.oneulsogae.common.mission.MissionType
import com.org.oneulsogae.core.mission.application.evaluator.WriteIntroductionMissionEvaluator
import com.org.oneulsogae.core.user.query.dto.UserDetailView
import com.org.oneulsogae.core.user.query.service.port.`in`.GetUserDetailUseCase
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

/**
 * [WriteIntroductionMissionEvaluator] 유닛 테스트.
 * 자기소개 길이(앞뒤 공백 제외) 100자 임계값과 지원 유형을 검증한다.
 */
class WriteIntroductionMissionEvaluatorTest : DescribeSpec({

	val getUserDetailUseCase: GetUserDetailUseCase = mockk()
	val evaluator = WriteIntroductionMissionEvaluator(getUserDetailUseCase)

	describe("isEligible") {
		it("소개가 100자 이상이면 자격이 있다") {
			val view: UserDetailView = mockk()
			every { view.introduction } returns "가".repeat(100)
			every { getUserDetailUseCase.findByUserId(1L) } returns view

			evaluator.isEligible(1L) shouldBe true
		}

		it("소개가 99자면 자격이 없다") {
			val view: UserDetailView = mockk()
			every { view.introduction } returns "가".repeat(99)
			every { getUserDetailUseCase.findByUserId(1L) } returns view

			evaluator.isEligible(1L) shouldBe false
		}

		it("앞뒤 공백을 뺀 길이로 판정한다 (공백 패딩은 자격 없음)") {
			val view: UserDetailView = mockk()
			every { view.introduction } returns "  " + "가".repeat(50) + "   "
			every { getUserDetailUseCase.findByUserId(1L) } returns view

			evaluator.isEligible(1L) shouldBe false
		}

		it("프로필이 없거나 소개가 null이면 자격이 없다") {
			every { getUserDetailUseCase.findByUserId(1L) } returns null

			evaluator.isEligible(1L) shouldBe false
		}
	}

	describe("supports") {
		it("WRITE_INTRODUCTION만 지원한다") {
			evaluator.supports(MissionType.WRITE_INTRODUCTION) shouldBe true
		}
	}
})
