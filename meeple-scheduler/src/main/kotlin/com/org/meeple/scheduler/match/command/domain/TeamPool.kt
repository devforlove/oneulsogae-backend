package com.org.meeple.scheduler.match.command.domain

import com.org.meeple.common.user.Gender
import com.org.meeple.scheduler.match.query.dto.CandidateTeam

/**
 * 팀 추천 배치의 인메모리 후보 팀 풀. `(성별, 권역)` 버킷에 teamId를 담는다.
 * 한 팀은 여러 유저에게 중복 추천될 수 있으므로 풀에서 제거하지 않는다(읽기 전용). 프레임워크에 의존하지 않는다.
 */
class TeamPool private constructor(
    private val teamIdsByKey: Map<BucketKey, List<Long>>,
) {

    /** [gender]·[regionId] 버킷의 후보 teamId 목록. (없으면 빈 리스트) */
    fun teamIdsOf(gender: Gender, regionId: Long): List<Long> =
        teamIdsByKey[BucketKey(gender, regionId)] ?: emptyList()

    private data class BucketKey(val gender: Gender, val regionId: Long)

    companion object {

        /** 후보 팀을 (성별, 권역) 버킷으로 묶어 풀을 만든다. */
        fun of(teams: List<CandidateTeam>): TeamPool {
            val teamIdsByKey: Map<BucketKey, List<Long>> = teams
                .groupBy({ team: CandidateTeam -> BucketKey(team.gender, team.regionId) }, { team: CandidateTeam -> team.teamId })
            return TeamPool(teamIdsByKey)
        }
    }
}
