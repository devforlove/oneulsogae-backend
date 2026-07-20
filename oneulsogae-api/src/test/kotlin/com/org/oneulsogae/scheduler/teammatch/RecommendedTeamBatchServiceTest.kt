package com.org.oneulsogae.scheduler.teammatch

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.scheduler.teammatch.command.application.RecommendedTeamBatchService
import com.org.oneulsogae.scheduler.common.command.application.port.out.RegionProximityPort
import com.org.oneulsogae.scheduler.common.command.application.port.out.RegionShuffler
import com.org.oneulsogae.scheduler.teammatch.command.application.port.out.SaveRecommendedTeamPort
import com.org.oneulsogae.scheduler.common.command.application.port.out.TimeGenerator
import com.org.oneulsogae.scheduler.teammatch.command.domain.RecommendedTeamBatchResult
import com.org.oneulsogae.scheduler.teammatch.query.dao.GetCandidateTeamDao
import com.org.oneulsogae.scheduler.teammatch.query.dao.GetRecommendableSoloUserDao
import com.org.oneulsogae.scheduler.teammatch.query.dao.GetRecommendedTeamHistoryDao
import com.org.oneulsogae.scheduler.teammatch.query.dao.GetRecommendedTeamRecordDao
import com.org.oneulsogae.scheduler.teammatch.query.dto.CandidateTeam
import com.org.oneulsogae.scheduler.teammatch.query.dto.RecommendableSoloUser
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

class RecommendedTeamBatchServiceTest : DescribeSpec({

    val fixedNow: LocalDateTime = LocalDateTime.of(2026, 6, 27, 9, 0)

    // 단일 대상·고정 후보로 배치를 구성한다. matchedTeamIds는 유저별 과거 매칭 상대. saves에 replace 호출이 기록된다.
    fun service(
        target: RecommendableSoloUser,
        candidates: List<CandidateTeam>,
        matchedTeamIds: Set<Long>,
        saves: MutableList<Triple<Long, Long, LocalDate>>,
    ): RecommendedTeamBatchService =
        RecommendedTeamBatchService(
            getRecommendableSoloUserDao = object : GetRecommendableSoloUserDao {
                override fun findRecommendableSoloUsers(loginAfter: LocalDateTime): List<RecommendableSoloUser> = listOf(target)
            },
            getRecommendedTeamRecordDao = object : GetRecommendedTeamRecordDao {
                override fun findUserIdsRecommendedOn(date: LocalDate): Set<Long> = emptySet()
            },
            getCandidateTeamDao = object : GetCandidateTeamDao {
                override fun findCandidateTeams(): List<CandidateTeam> = candidates
            },
            getRecommendedTeamHistoryDao = object : GetRecommendedTeamHistoryDao {
                override fun findMatchedTeamIds(userId: Long): Set<Long> = matchedTeamIds
            },
            saveRecommendedTeamPort = object : SaveRecommendedTeamPort {
                override fun replace(userId: Long, teamId: Long, recommendedDate: LocalDate) {
                    saves.add(Triple(userId, teamId, recommendedDate))
                }
            },
            regionProximityPort = object : RegionProximityPort {
                override fun refresh() {}
                override fun nearbyRegionIds(regionId: Long): List<Long> = listOf(regionId)
            },
            regionShuffler = RegionShuffler { regionIds: List<Long> -> regionIds },
            timeGenerator = object : TimeGenerator {
                override fun now(): LocalDateTime = fixedNow
            },
            random = Random(0),
        )

    describe("run - 유저별 재매칭 제외") {
        val target = RecommendableSoloUser(userId = 1L, gender = Gender.MALE, regionId = 1L)

        it("과거 매칭한 상대 팀은 추천하지 않고 같은 권역의 다른 팀을 고른다") {
            val saves: MutableList<Triple<Long, Long, LocalDate>> = mutableListOf()
            val candidates: List<CandidateTeam> = listOf(
                CandidateTeam(teamId = 100L, gender = Gender.FEMALE, regionId = 1L),
                CandidateTeam(teamId = 200L, gender = Gender.FEMALE, regionId = 1L),
            )

            service(target, candidates, matchedTeamIds = setOf(100L), saves).run()

            saves shouldContainExactly listOf(Triple(1L, 200L, LocalDate.of(2026, 6, 27)))
        }

        it("권역의 모든 후보가 과거 매칭 상대면 추천하지 않는다(skipped)") {
            val saves: MutableList<Triple<Long, Long, LocalDate>> = mutableListOf()
            val candidates: List<CandidateTeam> = listOf(CandidateTeam(teamId = 100L, gender = Gender.FEMALE, regionId = 1L))

            val result: RecommendedTeamBatchResult = service(target, candidates, matchedTeamIds = setOf(100L), saves).run()

            saves shouldBe emptyList()
            result.recommended shouldBe 0
            result.skipped shouldBe 1
        }
    }
})
