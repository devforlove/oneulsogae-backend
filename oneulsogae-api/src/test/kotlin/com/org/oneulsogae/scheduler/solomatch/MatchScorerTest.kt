package com.org.oneulsogae.scheduler.solomatch

import com.org.oneulsogae.common.user.DrinkingStatus
import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.common.user.MaritalStatus
import com.org.oneulsogae.common.user.Religion
import com.org.oneulsogae.common.user.SmokingStatus
import com.org.oneulsogae.common.match.selection.MatchScorer
import com.org.oneulsogae.common.match.selection.MatchScoringProfile
import com.org.oneulsogae.scheduler.solomatch.query.dto.MatchableUser
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import kotlin.random.Random

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
            val target: MatchScoringProfile = profile(idealSmokingStatus = SmokingStatus.NON_SMOKER)
            val candidate: MatchScoringProfile = profile(smokingStatus = SmokingStatus.NON_SMOKER)
            MatchScorer.mutualIdealFit(target, candidate) shouldBe 1.0
        }

        it("대상 이상형 1개 조건을 후보가 불충족, 후보는 이상형 없음 → (0.0+1.0)/2 = 0.5") {
            val target: MatchScoringProfile = profile(idealSmokingStatus = SmokingStatus.NON_SMOKER)
            val candidate: MatchScoringProfile = profile(smokingStatus = SmokingStatus.SMOKER)
            MatchScorer.mutualIdealFit(target, candidate) shouldBe 0.5
        }

        it("지정 조건 2개 중 1개만 충족 → 방향 점수 0.5") {
            val target: MatchScoringProfile = profile(
                idealSmokingStatus = SmokingStatus.NON_SMOKER,
                idealReligion = Religion.NONE,
            )
            val candidate: MatchScoringProfile = profile(
                smokingStatus = SmokingStatus.NON_SMOKER,   // 충족
                religion = Religion.BUDDHISM,                // 불충족
            )
            // 대상→후보 = 1/2 = 0.5, 후보→대상 = 이상형 없음 = 1.0 → (0.5+1.0)/2 = 0.75
            MatchScorer.mutualIdealFit(target, candidate) shouldBe 0.75
        }

        it("결혼 여부 이상형은 절대 조건(필터)이므로 점수에 반영하지 않는다") {
            // idealMaritalStatus만 지정 → 점수상 지정 조건 없음 = 1.0. (충족/불충족 여부와 무관)
            val target: MatchScoringProfile = profile(idealMaritalStatus = MaritalStatus.SINGLE)
            MatchScorer.mutualIdealFit(target, profile(maritalStatus = MaritalStatus.DIVORCED)) shouldBe 1.0
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
                age = 30, idealSmokingStatus = SmokingStatus.NON_SMOKER,
            )
            val candidate: MatchScoringProfile = profile(
                smokingStatus = SmokingStatus.NON_SMOKER, idealAgeMin = 28, idealAgeMax = 32,
            )
            MatchScorer.mutualIdealFit(target, candidate) shouldBe 1.0
        }
    }

    describe("recencyScore") {
        val loginAfter: LocalDateTime = LocalDateTime.of(2026, 6, 17, 12, 0)   // now - 2주
        val now: LocalDateTime = LocalDateTime.of(2026, 7, 1, 12, 0)
        it("now에 로그인=1.0, 창 시작=0.0, 중간=0.5") {
            MatchScorer.recencyScore(now, loginAfter, now) shouldBe 1.0
            MatchScorer.recencyScore(loginAfter, loginAfter, now) shouldBe 0.0
            MatchScorer.recencyScore(LocalDateTime.of(2026, 6, 24, 12, 0), loginAfter, now) shouldBe 0.5
        }
    }

    describe("combinedScore") {
        it("이상형 0.7 / 최근 0.3 가중합 (지역은 점수가 아니라 계층)") {
            MatchScorer.combinedScore(idealFit = 1.0, recencyScore = 0.0) shouldBe (0.7 plusOrMinus 1e-9)
            MatchScorer.combinedScore(idealFit = 0.0, recencyScore = 1.0) shouldBe (0.3 plusOrMinus 1e-9)
        }
    }

    describe("orderByScore") {
        fun user(userId: Long): MatchableUser =
            MatchableUser(userId, Gender.FEMALE, regionId = 1L, lastLoginAt = LocalDateTime.of(2026, 7, 1, 12, 0), companyName = null, refuseSameCompanyIntro = true)

        it("점수 내림차순으로 정렬한다(버킷이 다르면 결정적)") {
            val scored: List<Pair<MatchableUser, Double>> = listOf(
                user(1L) to 0.30,
                user(2L) to 0.90,
                user(3L) to 0.60,
            )
            MatchScorer.orderByScore(scored, Random(0)).map { u: MatchableUser -> u.userId } shouldContainExactly listOf(2L, 3L, 1L)
        }

        it("같은 버킷(0.05 이내) 안은 무작위지만 높은 버킷이 항상 앞선다") {
            // 0.90, 0.91 → 같은 버킷(18). 0.50 → 버킷 10. 앞 두 명이 어떤 순서든 셋째는 3L.
            val scored: List<Pair<MatchableUser, Double>> = listOf(
                user(1L) to 0.90,
                user(2L) to 0.91,
                user(3L) to 0.50,
            )
            val ordered: List<Long> = MatchScorer.orderByScore(scored, Random(42)).map { u: MatchableUser -> u.userId }
            ordered.last() shouldBe 3L
            ordered.take(2).sorted() shouldContainExactly listOf(1L, 2L)
        }
    }
})
