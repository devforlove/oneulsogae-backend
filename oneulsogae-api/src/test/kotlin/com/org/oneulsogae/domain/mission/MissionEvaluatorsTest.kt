package com.org.oneulsogae.domain.mission

import com.org.oneulsogae.common.mission.MissionType
import com.org.oneulsogae.core.mission.application.evaluator.MissionEvaluator
import com.org.oneulsogae.core.mission.application.evaluator.MissionEvaluators
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [MissionEvaluators] resolver 유닛 테스트.
 * 지원 평가자 선택과, 대응 평가자가 없을 때의 IllegalStateException을 검증한다.
 */
class MissionEvaluatorsTest : DescribeSpec({

	val supporting = object : MissionEvaluator {
		override fun supports(type: MissionType): Boolean = type == MissionType.WRITE_INTRODUCTION
		override fun isEligible(userId: Long): Boolean = true
	}

	describe("resolve") {
		it("유형을 지원하는 평가자를 반환한다") {
			MissionEvaluators(listOf(supporting)).resolve(MissionType.WRITE_INTRODUCTION) shouldBe supporting
		}

		it("대응 평가자가 없으면 IllegalStateException을 던진다") {
			shouldThrow<IllegalStateException> {
				MissionEvaluators(emptyList()).resolve(MissionType.WRITE_INTRODUCTION)
			}
		}
	}
})
