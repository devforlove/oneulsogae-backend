package com.org.meeple.scheduler.match.command.application

import com.org.meeple.common.user.Gender
import com.org.meeple.scheduler.match.command.application.port.out.MatchPoolPort
import com.org.meeple.scheduler.match.command.application.port.out.SaveMatchRecordPort
import com.org.meeple.scheduler.match.query.dao.MatchRecordDao
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 한 대상 사용자에게 소개 상대를 찾아 소개를 생성하는 책임을 담당한다.
 * 미리 적재해 둔 (반대 성별, 같은 권역) Redis 풀에서 후보를 한 명씩 꺼내(pop) 재소개 이력이 없는 첫 후보로 정하고,
 * 같은 권역에 후보가 마르면 지역 무관 성별 풀로 폴백한다.
 * 일일 배치 순회 자체는 [RunDailyMatchBatchService]가 맡고, 사용자 한 명에 대한 소개 로직만 이 클래스로 분리한다.
 */
@Component
class MatchIntroducer(
	private val matchRecordDao: MatchRecordDao,
	private val saveMatchRecordPort: SaveMatchRecordPort,
	private val matchPoolPort: MatchPoolPort,
) {

	/**
	 * [targetId] 사용자에게 소개 상대를 찾아 소개를 생성한다. 소개한 상대 userId를 반환하고, 후보를 못 찾으면 null.
	 * (호출 측은 반환된 상대 id로 "오늘 소개됨" 집합을 갱신해, 그 상대가 뒤이어 대상이 될 때 이중 소개를 막는다)
	 * 1순위로 반대 성별·**같은 권역**([regionCode]) 풀에서 찾고, 거기서 못 찾으면 2순위로 **지역 무관 성별 풀**로 폴백한다.
	 * 소개에 성공하면 두 사람을 (권역 풀 + 성별 풀) 모든 풀에서 빼, 같은 배치에서 다시 소개되지 않게 한다.
	 * (성별 풀로 뽑힌 상대의 권역은 [regionByUserId]로 알아내 그 사람의 권역 풀에서도 제거한다)
	 */
	fun introduce(
		targetId: Long,
		gender: Gender,
		regionCode: Int,
		regionByUserId: Map<Long, Int>,
		now: LocalDateTime,
	): Long? {
		val partnerGender: Gender = gender.opposite()
		val matchedPartnerId: Long =
			popFreshCandidate( // 1순위: 같은 권역 풀.
			targetId = targetId,
			gender = gender,
			pop = { matchPoolPort.pop(partnerGender, regionCode) },
			pushBack = { ids: List<Long> -> matchPoolPort.pushBack(partnerGender, regionCode, ids) },
		) ?: popFreshCandidate( // 2순위: 같은 권역에 후보가 마르면 지역 무관 성별 풀로 폴백.
				targetId = targetId,
				gender = gender,
				pop = { matchPoolPort.popByGender(partnerGender) },
				pushBack = { ids: List<Long> -> matchPoolPort.pushBackByGender(partnerGender, ids) },
			)
			?: return null

		saveMatchRecordPort.saveProposedMatch(
			requesterId = targetId,
			requesterGender = gender,
			partnerId = matchedPartnerId,
			now = now,
		)

		// 소개된 두 사람을 모든 풀(권역+성별)에서 제거해 같은 배치에서 다시 소개되지 않게 한다.
		removeFromAllPools(targetId, gender, regionCode)
		removeFromAllPools(matchedPartnerId, partnerGender, regionByUserId[matchedPartnerId])
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
				if (matchRecordDao.existsByPair(maleId, femaleId)) {
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

	/** 매칭된 [userId]를 권역 풀과 성별 풀 양쪽에서 제거한다. ([regionCode]가 null이면 권역 풀 제거는 생략) */
	private fun removeFromAllPools(userId: Long, gender: Gender, regionCode: Int?) {
		if (regionCode != null) matchPoolPort.remove(gender, regionCode, userId)
		matchPoolPort.removeByGender(gender, userId)
	}

	companion object {
		/**
		 * 한 대상에게 후보를 찾을 때 풀에서 꺼내(pop) 이력 검사를 시도하는 최대 횟수.
		 * 이력 있는 후보를 무한정 훑어 대상당 DB 조회가 폭증하는 것을 막는 상한이다.
		 * (pop이 무작위라 신선한 후보가 있으면 보통 몇 번 내에 걸린다)
		 */
		private const val MAX_CANDIDATE_ATTEMPTS = 3
	}
}
