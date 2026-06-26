package com.org.meeple.scheduler.match.command.domain

import com.org.meeple.common.user.Gender
import com.org.meeple.scheduler.match.query.dto.MatchableTeam

/**
 * 팀 매칭의 인메모리 후보 팀 풀. `(성별, 권역)` 버킷(최신 로그인 내림차순) + 가용 teamId 집합으로 구성한다.
 * 매칭된 팀을 [remove]로 가용에서 빼면 이후 [freshCandidates]·[contains]에서 즉시 제외된다. (이번 실행에서 한 팀 1회만 매칭)
 * 1:1 [MatchPool]의 팀 버전이다. 한 팀을 여러 유저에게 중복 추천하는 읽기전용 [TeamPool](추천 배치)과는 별개다.
 * 프레임워크에 의존하지 않는다.
 */
class TeamMatchPool private constructor(
	private val bucketsByKey: Map<BucketKey, List<MatchableTeam>>,
	private val available: MutableSet<Long>,
) {

	/** [gender]·[regionId] 버킷에서 아직 가용한 후보 팀을 최신 로그인순으로 돌려준다. */
	fun freshCandidates(gender: Gender, regionId: Long): List<MatchableTeam> =
		(bucketsByKey[BucketKey(gender, regionId)] ?: emptyList())
			.filter { team: MatchableTeam -> team.teamId in available }

	/** 매칭된 [team]을 가용에서 제거한다. */
	fun remove(team: MatchableTeam) {
		available.remove(team.teamId)
	}

	/** [team]이 아직 가용한지(=이번 실행에서 아직 짝지어지지 않았는지). */
	fun contains(team: MatchableTeam): Boolean =
		team.teamId in available

	private data class BucketKey(val gender: Gender, val regionId: Long)

	companion object {

		/** 후보 팀들을 (성별, 권역) 버킷(최신 로그인 내림차순)으로 묶어 풀을 만든다. */
		fun of(teams: List<MatchableTeam>): TeamMatchPool {
			val bucketsByKey: Map<BucketKey, List<MatchableTeam>> = teams
				.sortedByDescending { team: MatchableTeam -> team.lastLoginAt }
				.groupBy { team: MatchableTeam -> BucketKey(team.gender, team.regionId) }
			val available: MutableSet<Long> = teams.mapTo(mutableSetOf()) { team: MatchableTeam -> team.teamId }
			return TeamMatchPool(bucketsByKey, available)
		}
	}
}
