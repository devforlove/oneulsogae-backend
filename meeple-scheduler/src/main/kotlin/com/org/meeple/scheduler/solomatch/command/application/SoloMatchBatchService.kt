package com.org.meeple.scheduler.solomatch.command.application

import com.org.meeple.common.user.Gender
import com.org.meeple.scheduler.solomatch.command.application.port.`in`.RunSoloMatchBatchUseCase
import com.org.meeple.scheduler.common.command.application.port.out.RegionProximityPort
import com.org.meeple.scheduler.common.command.application.port.out.RegionShuffler
import com.org.meeple.scheduler.solomatch.command.application.port.out.SaveMatchRecordPort
import com.org.meeple.scheduler.common.command.application.port.out.TimeGenerator
import com.org.meeple.scheduler.solomatch.command.domain.SoloMatchBatchResult
import com.org.meeple.scheduler.solomatch.command.domain.MatchPool
import com.org.meeple.scheduler.solomatch.query.dao.GetMatchRecordDao
import com.org.meeple.scheduler.solomatch.query.dao.GetMatchableUserDao
import com.org.meeple.scheduler.solomatch.query.dto.MatchableUser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [RunSoloMatchBatchUseCase] 구현. 매일 정오에 도는 거리 기반 일일 매칭 배치.
 *
 * "2주 내 활성 + 오늘 미매칭 + 성사 상태 아님" 유저를 한 번 적재해 [MatchPool]을 만들고,
 * 대상을 순회하며 [RegionProximityPort.nearbyRegionIds]로 가까운 지역부터 반대 성별 후보를 꺼내
 * 재소개 이력([GetMatchRecordDao.existsByPair])이 없는 첫 후보와 PROPOSED 소개를 만든다. 매칭된 두 사람은 풀에서 뺀다.
 * 한 사용자의 실패가 다른 사용자에 전파되지 않도록 대상 단위로 격리하고, 예외만 failed로 집계한다.
 */
@Service
class SoloMatchBatchService(
	private val getMatchableUserDao: GetMatchableUserDao,
	private val getMatchRecordDao: GetMatchRecordDao,
	private val saveMatchRecordPort: SaveMatchRecordPort,
	private val regionProximityPort: RegionProximityPort,
	private val timeGenerator: TimeGenerator,
	private val regionShuffler: RegionShuffler,
) : RunSoloMatchBatchUseCase {

	private val log: Logger = LoggerFactory.getLogger(javaClass)

	override fun run(): SoloMatchBatchResult {
		val now: LocalDateTime = timeGenerator.now()
		val loginAfter: LocalDateTime = now.minusWeeks(RECENT_LOGIN_WEEKS)
		val today: LocalDate = now.toLocalDate()

		// 근접·유저분포 스냅샷을 최신화한다. (온보딩 경로도 이득)
		regionProximityPort.refresh()

		// 신규 소개에서 제외할 유저: 이미 성사(MATCHED) 상태 + 오늘 한 번이라도 소개된 유저.
		val excluded: Set<Long> =
			getMatchRecordDao.findMatchedUserIds().values + getMatchRecordDao.findUserIdsIntroducedOn(today)

		val matchables: List<MatchableUser> = getMatchableUserDao.findMatchableUsers(loginAfter)
			.filterNot { user: MatchableUser -> user.userId in excluded }
		val pool: MatchPool = MatchPool.of(matchables)

		var recommended = 0
		var skipped = 0
		var failed = 0
		for (target: MatchableUser in matchables) {
			if (!pool.contains(target)) continue // 이번 실행에서 이미 짝지어진 유저
			try {
				val partner: MatchableUser? = findNearestFreshPartner(target, pool)
				if (partner == null) {
					skipped++
					continue
				}
				saveMatchRecordPort.saveProposedMatch(target.userId, target.gender, partner.userId, now)
				pool.remove(target)
				pool.remove(partner)
				recommended++
			} catch (e: Exception) {
				failed++
				log.warn("일일 매칭 처리 실패 userId={}", target.userId, e)
			}
		}

		val result: SoloMatchBatchResult = SoloMatchBatchResult(targets = matchables.size, recommended = recommended, skipped = skipped, failed = failed)
		log.info("일일 매칭 배치 완료: {}", result)
		return result
	}

	/** [target] 지역에서 가까운 순 상위 N개 지역을 무작위 순서로 뒤져, 재소개 이력이 없는 첫 후보를 찾는다. (없으면 null) */
	private fun findNearestFreshPartner(target: MatchableUser, pool: MatchPool): MatchableUser? {
		val partnerGender: Gender = target.gender.opposite()
		// 후보가 있는 지역만 추려 셔플·순회한다. (풀에 상대 성별 후보가 없는 지역은 헛순회라 건너뛴다)
		val populatedRegions: Set<Long> = pool.regionsWith(partnerGender)
		val nearbyPopulated: List<Long> = regionProximityPort.nearbyRegionIds(target.regionId)
			.filter { regionId: Long -> regionId in populatedRegions }
		val regionOrder: List<Long> = regionShuffler.shuffleNearest(nearbyPopulated)
		for (regionId: Long in regionOrder) {
			for (candidate: MatchableUser in pool.freshCandidates(partnerGender, regionId)) {
				if (!getMatchRecordDao.existsByPair(target.userId, candidate.userId)) return candidate
			}
		}
		return null
	}

	companion object {
		/** 후보로 인정하는 최근 로그인 기간(주). */
		private const val RECENT_LOGIN_WEEKS = 2L
	}
}
