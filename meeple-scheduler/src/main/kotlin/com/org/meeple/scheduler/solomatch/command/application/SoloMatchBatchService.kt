package com.org.meeple.scheduler.solomatch.command.application

import com.org.meeple.common.user.Gender
import com.org.meeple.common.match.selection.MatchScoringProfile
import com.org.meeple.common.match.selection.MatchSelector
import com.org.meeple.scheduler.solomatch.command.application.port.`in`.RunSoloMatchBatchUseCase
import com.org.meeple.scheduler.common.command.application.port.out.NoIntroductionAlarmPort
import com.org.meeple.scheduler.common.command.application.port.out.RegionProximityPort
import com.org.meeple.scheduler.solomatch.command.application.port.out.SaveMatchRecordPort
import com.org.meeple.scheduler.common.command.application.port.out.TimeGenerator
import com.org.meeple.scheduler.solomatch.command.domain.MatchPool
import com.org.meeple.scheduler.solomatch.command.domain.SoloMatchBatchResult
import com.org.meeple.scheduler.solomatch.query.dao.GetMatchRecordDao
import com.org.meeple.scheduler.solomatch.query.dao.GetMatchScoringProfileDao
import com.org.meeple.scheduler.solomatch.query.dao.GetMatchableUserDao
import com.org.meeple.scheduler.solomatch.query.dto.MatchableUser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * [RunSoloMatchBatchUseCase] 구현. 매일 정오에 도는 일일 매칭 배치.
 *
 * "2주 내 활성 + 오늘 미매칭 + 성사 상태 아님" 유저를 한 번 적재해 [MatchPool]을 만들고,
 * 스코어링 프로필([GetMatchScoringProfileDao])을 1회 적재한다. 대상마다 가용 반대 성별 후보 전체를
 * 이상형·거리·최근 종합 점수([MatchSelector])로 정렬해, 재소개 이력([GetMatchRecordDao.existsByPair])이 없는
 * 최고점 후보와 PROPOSED 소개를 만든다. 이상형은 필터가 아니라 우선순위라 안 맞아도 후보가 있으면 소개한다.
 * 한 사용자의 실패가 다른 사용자에 전파되지 않도록 대상 단위로 격리하고, 예외만 failed로 집계한다.
 */
@Service
class SoloMatchBatchService(
	private val getMatchableUserDao: GetMatchableUserDao,
	private val getMatchScoringProfileDao: GetMatchScoringProfileDao,
	private val getMatchRecordDao: GetMatchRecordDao,
	private val saveMatchRecordPort: SaveMatchRecordPort,
	private val regionProximityPort: RegionProximityPort,
	private val timeGenerator: TimeGenerator,
	private val noIntroductionAlarmPort: NoIntroductionAlarmPort,
	private val random: Random = Random.Default,
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
		// 이상형 우선순위 스코어링 프로필을 대상 전체에 대해 1회 적재한다. (user_details 없는 유저는 맵에 없음 → 선호 없음 취급)
		val profiles: Map<Long, MatchScoringProfile> =
			getMatchScoringProfileDao.load(matchables.mapTo(mutableSetOf()) { user: MatchableUser -> user.userId }, today)

		var recommended = 0
		var skipped = 0
		var failed = 0
		for (target: MatchableUser in matchables) {
			if (!pool.contains(target)) continue // 이번 실행에서 이미 짝지어진 유저
			try {
				val partner: MatchableUser? = findBestFreshPartner(target, pool, profiles, now, loginAfter)
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

		// 루프가 끝난 뒤 풀에 남은(=끝까지 소개받지 못한) 유저에게만 "오늘 소개 없음" 알람을 보낸다.
		noIntroductionAlarmPort.notifySoloUnmatched(pool.remainingUserIds(), now)

		val result: SoloMatchBatchResult = SoloMatchBatchResult(targets = matchables.size, recommended = recommended, skipped = skipped, failed = failed)
		log.info("일일 매칭 배치 완료: {}", result)
		return result
	}

	/**
	 * [target]의 가용 반대 성별 후보 전체를 이상형·거리·최근 종합 점수로 정렬해,
	 * 재소개 이력이 없는 최고점 후보를 돌려준다. (없으면 null) 점수 동점군은 무작위로 섞는다.
	 */
	private fun findBestFreshPartner(
		target: MatchableUser,
		pool: MatchPool,
		profiles: Map<Long, MatchScoringProfile>,
		now: LocalDateTime,
		loginAfter: LocalDateTime,
	): MatchableUser? {
		val partnerGender: Gender = target.gender.opposite()
		// 대상 지역 기준 근접 순위(같은 지역=0). 좌표 없는 지역이면 빈 리스트라 거리 점수는 전원 0이 된다.
		val nearby: List<Long> = regionProximityPort.nearbyRegionIds(target.regionId)
		val rankByRegion: Map<Long, Int> = nearby.withIndex().associate { (index: Int, regionId: Long) -> regionId to index }

		return MatchSelector.selectBest(
			targetProfile = profiles[target.userId],
			targetCompanyName = target.companyName,
			targetRefusesSameCompanyIntro = target.refuseSameCompanyIntro,
			candidates = pool.availableCandidates(partnerGender),
			profileOf = { candidate: MatchableUser -> profiles[candidate.userId] },
			regionRankByRegionId = rankByRegion,
			regionCount = nearby.size,
			now = now,
			loginAfter = loginAfter,
			random = random,
			isExcluded = { candidate: MatchableUser -> getMatchRecordDao.existsByPair(target.userId, candidate.userId) },
		)
	}

	companion object {
		/** 후보로 인정하는 최근 로그인 기간(주). */
		private const val RECENT_LOGIN_WEEKS = 2L
	}
}
