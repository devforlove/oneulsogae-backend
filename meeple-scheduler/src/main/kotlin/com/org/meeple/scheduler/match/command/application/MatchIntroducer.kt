package com.org.meeple.scheduler.match.command.application

import com.org.meeple.common.user.Gender
import com.org.meeple.scheduler.match.command.application.port.out.MatchPoolPort
import com.org.meeple.scheduler.match.command.application.port.out.RegionProximityPort
import com.org.meeple.scheduler.match.command.application.port.out.SaveMatchRecordPort
import com.org.meeple.scheduler.match.query.dao.GetMatchRecordDao
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 한 대상 사용자에게 소개 상대를 찾아 소개를 생성하는 책임을 담당한다.
 * 대상 지역에서 **가까운 지역 순서**로 (반대 성별, 지역) Redis 풀을 순회하며, 재소개 이력이 없는 첫 후보를 상대로 정한다.
 * 같은 지역 풀 내에서는 Redis Set의 무작위 pop으로 동순위를 타이브레이크한다.
 * 일일 배치 순회 자체는 [RunDailyMatchBatchService]가 맡고, 사용자 한 명에 대한 소개 로직만 이 클래스로 분리한다.
 */
@Component
class MatchIntroducer(
	private val getMatchRecordDao: GetMatchRecordDao,
	private val saveMatchRecordPort: SaveMatchRecordPort,
	private val matchPoolPort: MatchPoolPort,
	private val regionProximityPort: RegionProximityPort,
) {

	/**
	 * [targetId] 사용자에게 소개 상대를 찾아 소개를 생성한다. 소개한 상대 userId를 반환하고, 후보를 못 찾으면 null.
	 * (호출 측은 반환된 상대 id로 "오늘 소개됨" 집합을 갱신해, 그 상대가 뒤이어 대상이 될 때 이중 소개를 막는다)
	 * [regionId]에서 가까운 지역 순서대로 반대 성별 풀을 순회해 재소개 이력 없는 첫 후보를 고른다.
	 * 소개에 성공하면 두 사람을 각자의 (성별, 지역) 풀에서 빼, 같은 배치에서 다시 소개되지 않게 한다.
	 * (상대의 지역은 [regionByUserId]로 알아내 그 사람의 지역 풀에서 제거한다)
	 */
	fun introduce(
		targetId: Long,
		gender: Gender,
		regionId: Long,
		regionByUserId: Map<Long, Long>,
		now: LocalDateTime,
	): Long? {
		val partnerGender: Gender = gender.opposite()

		// 가까운 지역부터 순회하며 첫 신선 후보를 찾는다. (각 지역 풀에서 최대 MAX_CANDIDATE_ATTEMPTS번 pop)
		val matchedPartnerId: Long = regionProximityPort.nearbyRegionIds(regionId)
			.firstNotNullOfOrNull { candidateRegionId: Long ->
				popFreshCandidate(
					targetId = targetId,
					gender = gender,
					pop = { matchPoolPort.pop(partnerGender, candidateRegionId) },
					pushBack = { ids: List<Long> -> matchPoolPort.pushBack(partnerGender, candidateRegionId, ids) },
				)
			}
			?: return null

		saveMatchRecordPort.saveProposedMatch(
			requesterId = targetId,
			requesterGender = gender,
			partnerId = matchedPartnerId,
			now = now,
		)

		// 소개된 두 사람을 각자의 (성별, 지역) 풀에서 제거해 같은 배치에서 다시 소개되지 않게 한다.
		matchPoolPort.remove(gender, regionId, targetId)
		regionByUserId[matchedPartnerId]?.let { partnerRegionId: Long ->
			matchPoolPort.remove(partnerGender, partnerRegionId, matchedPartnerId)
		}
		return matchedPartnerId
	}

	/**
	 * 주어진 [pop]/[pushBack]으로 한 풀에서 후보를 한 명씩 꺼내, 재소개 이력이 없는 첫 후보를 고른다.
	 * 풀 전체를 끝까지 까지 않고 **최대 [MAX_CANDIDATE_ATTEMPTS]번만** 시도한다. (이력 있는 후보를 무한정 훑어
	 * 대상당 DB 조회가 O(풀 크기)로 폭증하는 것을 막는다) 이력이 있어 건너뛴 후보들은 풀에 되돌린다.
	 */
	private fun popFreshCandidate(
		targetId: Long,
		gender: Gender,
		pop: () -> Long?,
		pushBack: (List<Long>) -> Unit,
	): Long? {
		val rejected: MutableList<Long> = mutableListOf()
		var found: Long? = null
		try {
			var attempts = 0
			while (attempts < MAX_CANDIDATE_ATTEMPTS) {
				val candidate: Long = pop() ?: break
				attempts++
				// 남/녀 자리를 배치해 (maleUserId, femaleUserId) 쌍으로 재소개 이력을 확인한다.
				val maleId: Long = if (gender == Gender.MALE) targetId else candidate
				val femaleId: Long = if (gender == Gender.MALE) candidate else targetId
				if (getMatchRecordDao.existsByPair(maleId, femaleId)) {
					rejected.add(candidate)
					continue
				}
				found = candidate
				break
			}
		} finally {
			pushBack(rejected)
		}
		return found
	}

	companion object {
		/**
		 * 한 지역 풀에서 후보를 찾을 때 pop해 이력 검사를 시도하는 최대 횟수.
		 * 이력 있는 후보를 무한정 훑어 대상당 DB 조회가 폭증하는 것을 막는 상한이다.
		 * (pop이 무작위라 신선한 후보가 있으면 보통 몇 번 내에 걸린다)
		 */
		private const val MAX_CANDIDATE_ATTEMPTS = 3
	}
}
