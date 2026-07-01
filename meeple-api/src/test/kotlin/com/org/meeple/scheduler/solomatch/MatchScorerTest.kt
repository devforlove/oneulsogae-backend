package com.org.meeple.scheduler.solomatch

import com.org.meeple.common.user.DrinkingStatus
import com.org.meeple.common.user.MaritalStatus
import com.org.meeple.common.user.Religion
import com.org.meeple.common.user.SmokingStatus
import com.org.meeple.scheduler.solomatch.command.domain.MatchScorer
import com.org.meeple.scheduler.solomatch.query.dto.MatchScoringProfile
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

/**
 * [MatchScorer] 유닛 테스트. 이상형 부합(양방향)·거리·최근·종합 점수와 버킷 정렬을 프레임워크 없이 검증한다.
 */
class MatchScorerTest : DescribeSpec({

    // 지정한 조건만 채운 프로필 헬퍼. (나머지는 null = 미지정/상관없음)
    fun profile(
        userId: Long = 1L,
        age: Int? = null,
        height: Int? = null,
        maritalStatus: MaritalStatus? = null,
        smokingStatus: SmokingStatus? = null,
        drinkingStatus: DrinkingStatus? = null,
        religion: Religion? = null,
        idealAgeMin: Int? = null,
        idealAgeMax: Int? = null,
        idealHeightMin: Int? = null,
        idealHeightMax: Int? = null,
        idealMaritalStatus: MaritalStatus? = null,
        idealSmokingStatus: SmokingStatus? = null,
        idealDrinkingStatus: DrinkingStatus? = null,
        idealReligion: Religion? = null,
    ): MatchScoringProfile = MatchScoringProfile(
        userId, age, height, maritalStatus, smokingStatus, drinkingStatus, religion,
        idealAgeMin, idealAgeMax, idealHeightMin, idealHeightMax,
        idealMaritalStatus, idealSmokingStatus, idealDrinkingStatus, idealReligion,
    )

    describe("mutualIdealFit") {

        it("양쪽 다 이상형이 없으면 1.0(선호 없음)") {
            MatchScorer.mutualIdealFit(profile(), profile()) shouldBe 1.0
        }

        it("한 방향만 평가: 대상 이상형 1개 조건을 후보가 충족하고 후보는 이상형 없음 → (1.0+1.0)/2 = 1.0") {
            val target: MatchScoringProfile = profile(idealMaritalStatus = MaritalStatus.SINGLE)
            val candidate: MatchScoringProfile = profile(maritalStatus = MaritalStatus.SINGLE)
            MatchScorer.mutualIdealFit(target, candidate) shouldBe 1.0
        }

        it("대상 이상형 1개 조건을 후보가 불충족, 후보는 이상형 없음 → (0.0+1.0)/2 = 0.5") {
            val target: MatchScoringProfile = profile(idealMaritalStatus = MaritalStatus.SINGLE)
            val candidate: MatchScoringProfile = profile(maritalStatus = MaritalStatus.DIVORCED)
            MatchScorer.mutualIdealFit(target, candidate) shouldBe 0.5
        }

        it("지정 조건 2개 중 1개만 충족 → 방향 점수 0.5") {
            val target: MatchScoringProfile = profile(
                idealMaritalStatus = MaritalStatus.SINGLE,
                idealReligion = Religion.NONE,
            )
            val candidate: MatchScoringProfile = profile(
                maritalStatus = MaritalStatus.SINGLE,   // 충족
                religion = Religion.BUDDHISM,            // 불충족
            )
            // 대상→후보 = 1/2 = 0.5, 후보→대상 = 이상형 없음 = 1.0 → (0.5+1.0)/2 = 0.75
            MatchScorer.mutualIdealFit(target, candidate) shouldBe 0.75
        }

        it("나이 범위: 경계 포함이면 충족") {
            val target: MatchScoringProfile = profile(idealAgeMin = 27, idealAgeMax = 35)
            MatchScorer.mutualIdealFit(target, profile(age = 27)) shouldBe 1.0
            MatchScorer.mutualIdealFit(target, profile(age = 35)) shouldBe 1.0
            MatchScorer.mutualIdealFit(target, profile(age = 26)) shouldBe 0.5   // (0+1)/2
        }

        it("후보 속성이 결측(null)이면 그 조건은 미충족") {
            val target: MatchScoringProfile = profile(idealHeightMin = 160, idealHeightMax = 175)
            MatchScorer.mutualIdealFit(target, profile(height = null)) shouldBe 0.5   // (0+1)/2
        }

        it("양방향 모두 충족하면 1.0") {
            val target: MatchScoringProfile = profile(
                age = 30, idealMaritalStatus = MaritalStatus.SINGLE,
            )
            val candidate: MatchScoringProfile = profile(
                maritalStatus = MaritalStatus.SINGLE, idealAgeMin = 28, idealAgeMax = 32,
            )
            MatchScorer.mutualIdealFit(target, candidate) shouldBe 1.0
        }
    }
})
