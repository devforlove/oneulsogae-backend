package com.org.meeple.scheduler.teammatch.command.domain

import com.org.meeple.common.user.Gender
import com.org.meeple.scheduler.teammatch.query.dto.MatchableTeam

/**
 * 팀 매칭의 인메모리 후보 팀 풀. `(성별, 권역)` 버킷(최신 로그인 내림차순) + 가용 teamId 집합으로 구성한다.
 * 매칭된 팀을 [remove]로 가용에서 빼면 이후 [freshCandidates]·[contains]에서 즉시 제외된다. (이번 실행에서 한 팀 1회만 매칭)
 * 1:1 [MatchPool]의 팀 버전이다. 한 팀을 여러 유저에게 중복 추천하는 읽기전용 [TeamPool](추천 배치)과는 별개다.
 * 프레임워크에 의존하지 않는다.
 */
class TeamMatchPool private constructor(
	private val bucketsByKey: Map<BucketKey, List<MatchableTeam>>,
	private val regionsByGender: Map<Gender, Set<Long>>,
	private val available: MutableSet<Long>,
) {

	/** [gender]·[regionId] 버킷에서 아직 가용한 후보 팀을 최신 로그인순으로 돌려준다. */
	fun freshCandidates(gender: Gender, regionId: Long): List<MatchableTeam> =
		(bucketsByKey[BucketKey(gender, regionId)] ?: emptyList())
			.filter { team: MatchableTeam -> team.teamId in available }

	/** [gender] 후보 팀이 (하나라도) 존재하는 지역 집합. (후보 없는 지역의 헛순회를 건너뛰는 데 쓴다) */
	fun regionsWith(gender: Gender): Set<Long> =
		regionsByGender[gender] ?: emptySet()

	/** 매칭된 [team]을 가용에서 제거한다. */
	fun remove(team: MatchableTeam) {
		available.remove(team.teamId)
	}

	/** [team]이 아직 가용한지(=이번 실행에서 아직 짝지어지지 않았는지). */
	fun contains(team: MatchableTeam): Boolean =
		team.teamId in available

	/** 배치 종료 후 아직 가용한(=끝까지 짝지어지지 않은) teamId 전체. (오늘 소개를 못 받은 팀) */
	fun remainingTeamIds(): Set<Long> =
		available.toSet()

	private data class BucketKey(val gender: Gender, val regionId: Long)

	companion object {

		/** 후보 팀들을 (성별, 권역) 버킷(최신 로그인 내림차순)으로 묶어 풀을 만든다. */
		fun of(teams: List<MatchableTeam>): TeamMatchPool {
			val bucketsByKey: Map<BucketKey, List<MatchableTeam>> = teams
				.sortedByDescending { team: MatchableTeam -> team.lastLoginAt }
				.groupBy { team: MatchableTeam -> BucketKey(team.gender, team.regionId) }
			// 성별별 "후보 팀이 있는 권역" 집합을 미리 만들어, 후보 없는 권역의 헛순회를 O(1)로 건너뛴다.
			val regionsByGender: Map<Gender, Set<Long>> = bucketsByKey.keys
				.groupBy { key: BucketKey -> key.gender }
				.mapValues { (_, keys: List<BucketKey>) -> keys.mapTo(mutableSetOf()) { key: BucketKey -> key.regionId } }
			val available: MutableSet<Long> = teams.mapTo(mutableSetOf()) { team: MatchableTeam -> team.teamId }
			return TeamMatchPool(bucketsByKey, regionsByGender, available)
		}
	}
}
