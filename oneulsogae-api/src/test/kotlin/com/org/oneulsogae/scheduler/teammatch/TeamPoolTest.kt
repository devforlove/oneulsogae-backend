package com.org.oneulsogae.scheduler.teammatch

import com.org.oneulsogae.common.user.Gender
import com.org.oneulsogae.scheduler.teammatch.command.domain.TeamPool
import com.org.oneulsogae.scheduler.teammatch.query.dto.CandidateTeam
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class TeamPoolTest : DescribeSpec({

    fun team(id: Long, gender: Gender, regionId: Long): CandidateTeam =
        CandidateTeam(teamId = id, gender = gender, regionId = regionId)

    describe("teamIdsOf") {

        it("같은 (성별, 권역) 버킷의 teamId만 돌려준다") {
            val a: CandidateTeam = team(100L, Gender.FEMALE, 1L)
            val b: CandidateTeam = team(101L, Gender.FEMALE, 1L)
            val pool: TeamPool = TeamPool.of(listOf(a, b))

            pool.teamIdsOf(Gender.FEMALE, 1L) shouldContainExactlyInAnyOrder listOf(100L, 101L)
        }

        it("다른 성별/권역은 섞이지 않는다") {
            val femaleRegion1: CandidateTeam = team(100L, Gender.FEMALE, 1L)
            val femaleRegion2: CandidateTeam = team(101L, Gender.FEMALE, 2L)
            val maleRegion1: CandidateTeam = team(102L, Gender.MALE, 1L)
            val pool: TeamPool = TeamPool.of(listOf(femaleRegion1, femaleRegion2, maleRegion1))

            pool.teamIdsOf(Gender.FEMALE, 1L) shouldBe listOf(100L)
        }

        it("해당 버킷이 없으면 빈 리스트") {
            val pool: TeamPool = TeamPool.of(emptyList())

            pool.teamIdsOf(Gender.MALE, 9L).shouldBeEmpty()
        }
    }

    describe("regionsWith") {

        it("그 성별 후보 팀이 있는 권역만 돌려준다 (다른 성별·없는 권역은 제외)") {
            val femaleRegion1: CandidateTeam = team(100L, Gender.FEMALE, 1L)
            val femaleRegion2: CandidateTeam = team(101L, Gender.FEMALE, 2L)
            val maleRegion3: CandidateTeam = team(102L, Gender.MALE, 3L)
            val pool: TeamPool = TeamPool.of(listOf(femaleRegion1, femaleRegion2, maleRegion3))

            pool.regionsWith(Gender.FEMALE) shouldContainExactlyInAnyOrder setOf(1L, 2L)
            pool.regionsWith(Gender.MALE) shouldContainExactlyInAnyOrder setOf(3L)
        }

        it("그 성별 후보 팀이 없으면 빈 집합") {
            val pool: TeamPool = TeamPool.of(listOf(team(100L, Gender.FEMALE, 1L)))

            pool.regionsWith(Gender.MALE).shouldBeEmpty()
        }
    }
})
