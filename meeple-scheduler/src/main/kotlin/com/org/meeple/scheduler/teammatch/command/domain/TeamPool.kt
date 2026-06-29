package com.org.meeple.scheduler.teammatch.command.domain

import com.org.meeple.common.user.Gender
import com.org.meeple.scheduler.teammatch.query.dto.CandidateTeam

/**
 * 팀 추천 배치의 인메모리 후보 팀 풀. `(성별, 권역)` 버킷에 teamId를 담는다.
 * 한 팀은 여러 유저에게 중복 추천될 수 있으므로 풀에서 제거하지 않는다(읽기 전용). 프레임워크에 의존하지 않는다.
 */
class TeamPool private constructor(
    private val teamIdsByKey: Map<BucketKey, List<Long>>,
    private val regionsByGender: Map<Gender, Set<Long>>,
) {

    /** [gender]·[regionId] 버킷의 후보 teamId 목록. (없으면 빈 리스트) */
    fun teamIdsOf(gender: Gender, regionId: Long): List<Long> =
        teamIdsByKey[BucketKey(gender, regionId)] ?: emptyList()

    /** [gender] 후보 팀이 (하나라도) 존재하는 권역 집합. (후보 팀 없는 권역의 헛순회를 건너뛰는 데 쓴다) */
    fun regionsWith(gender: Gender): Set<Long> =
        regionsByGender[gender] ?: emptySet()

    private data class BucketKey(val gender: Gender, val regionId: Long)

    companion object {

        /** 후보 팀을 (성별, 권역) 버킷으로 묶어 풀을 만든다. */
        fun of(teams: List<CandidateTeam>): TeamPool {
            val teamIdsByKey: Map<BucketKey, List<Long>> = teams
                .groupBy({ team: CandidateTeam -> BucketKey(team.gender, team.regionId) }, { team: CandidateTeam -> team.teamId })
            // 성별별 "후보 팀이 있는 권역" 집합을 미리 만들어, 후보 팀 없는 권역의 헛순회를 O(1)로 건너뛴다.
            val regionsByGender: Map<Gender, Set<Long>> = teamIdsByKey.keys
                .groupBy { key: BucketKey -> key.gender }
                .mapValues { (_, keys: List<BucketKey>) -> keys.mapTo(mutableSetOf()) { key: BucketKey -> key.regionId } }
            return TeamPool(teamIdsByKey, regionsByGender)
        }
    }
}
