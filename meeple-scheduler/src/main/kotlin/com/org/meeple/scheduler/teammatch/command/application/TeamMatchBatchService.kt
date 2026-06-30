package com.org.meeple.scheduler.teammatch.command.application

import com.org.meeple.common.user.Gender
import com.org.meeple.scheduler.teammatch.command.application.port.`in`.RunTeamMatchBatchUseCase
import com.org.meeple.scheduler.common.command.application.port.out.NoIntroductionAlarmPort
import com.org.meeple.scheduler.common.command.application.port.out.RegionProximityPort
import com.org.meeple.scheduler.common.command.application.port.out.RegionShuffler
import com.org.meeple.scheduler.teammatch.command.application.port.out.SaveTeamMatchRecordPort
import com.org.meeple.scheduler.common.command.application.port.out.TimeGenerator
import com.org.meeple.scheduler.teammatch.command.domain.TeamMatchBatchResult
import com.org.meeple.scheduler.teammatch.command.domain.TeamMatchPool
import com.org.meeple.scheduler.teammatch.query.dao.GetMatchableTeamDao
import com.org.meeple.scheduler.teammatch.query.dao.GetTeamMatchRecordDao
import com.org.meeple.scheduler.teammatch.query.dto.MatchableTeam
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * [RunTeamMatchBatchUseCase] 구현. 매일 도는 거리 기반 일일 팀(2:2) 매칭 배치. (솔로 매칭 배치와 동일 골격)
 *
 * "ACTIVE 팀 + 구성원 한 명이라도 2주 내 로그인 + 오늘 미소개 + 성사 상태 아님" 팀을 한 번 적재해 [TeamMatchPool]을 만들고,
 * 대상을 순회하며 [RegionProximityPort.nearbyRegionIds]로 가까운 권역부터 반대 성별 팀 후보를 꺼내
 * 재소개 이력([GetTeamMatchRecordDao.existsByPair])이 없는 첫 후보와 PROPOSED 소개를 만든다. 매칭된 두 팀은 풀에서 뺀다.
 * 한 팀의 실패가 다른 팀에 전파되지 않도록 대상 단위로 격리하고, 예외만 failed로 집계한다.
 */
@Service
class TeamMatchBatchService(
	private val getMatchableTeamDao: GetMatchableTeamDao,
	private val getTeamMatchRecordDao: GetTeamMatchRecordDao,
	private val saveTeamMatchRecordPort: SaveTeamMatchRecordPort,
	private val regionProximityPort: RegionProximityPort,
	private val timeGenerator: TimeGenerator,
	private val regionShuffler: RegionShuffler,
	private val noIntroductionAlarmPort: NoIntroductionAlarmPort,
) : RunTeamMatchBatchUseCase {

	private val log: Logger = LoggerFactory.getLogger(javaClass)

	override fun run(): TeamMatchBatchResult {
		val now: LocalDateTime = timeGenerator.now()
		val loginAfter: LocalDateTime = now.minusWeeks(RECENT_LOGIN_WEEKS)
		val today: LocalDate = now.toLocalDate()

		// 근접 스냅샷을 최신화한다. (가까운 권역 순서 계산의 기준)
		regionProximityPort.refresh()

		// 신규 소개에서 제외할 팀: 이미 성사(MATCHED) 상태 + 오늘 한 번이라도 소개된 팀.
		val excluded: Set<Long> =
			getTeamMatchRecordDao.findMatchedTeamIds().values + getTeamMatchRecordDao.findTeamIdsIntroducedOn(today)

		val matchables: List<MatchableTeam> = getMatchableTeamDao.findMatchableTeams(loginAfter)
			.filterNot { team: MatchableTeam -> team.teamId in excluded }
		val pool: TeamMatchPool = TeamMatchPool.of(matchables)

		var recommended = 0
		var skipped = 0
		var failed = 0
		for (target: MatchableTeam in matchables) {
			if (!pool.contains(target)) continue // 이번 실행에서 이미 짝지어진 팀
			try {
				val partner: MatchableTeam? = findNearestFreshPartner(target, pool)
				if (partner == null) {
					skipped++
					continue
				}
				saveTeamMatchRecordPort.saveProposedTeamMatch(target.teamId, partner.teamId, now)
				pool.remove(target)
				pool.remove(partner)
				recommended++
			} catch (e: Exception) {
				failed++
				log.warn("일일 팀 매칭 처리 실패 teamId={}", target.teamId, e)
			}
		}

		// 루프가 끝난 뒤 풀에 남은(=끝까지 소개받지 못한) 팀의 활성 구성원에게만 "오늘 소개 없음" 알람을 보낸다.
		// (skipped 카운터는 이후 다른 팀의 짝으로 매칭될 수 있어 부정확하므로 풀 잔여로 정확히 판정한다)
		noIntroductionAlarmPort.notifyTeamUnmatched(pool.remainingTeamIds())

		val result: TeamMatchBatchResult = TeamMatchBatchResult(targets = matchables.size, recommended = recommended, skipped = skipped, failed = failed)
		log.info("일일 팀 매칭 배치 완료: {}", result)
		return result
	}

	/** [target] 권역에서 가까운 순 상위 N권역을 무작위 순서로 뒤져, 반대 성별 + 재소개 이력이 없는 첫 후보 팀을 찾는다. (없으면 null) */
	private fun findNearestFreshPartner(target: MatchableTeam, pool: TeamMatchPool): MatchableTeam? {
		// 팀은 동성 구성이므로, 대상 팀의 반대 성별 = 상대 팀의 성별.
		val partnerGender: Gender = target.gender.opposite()
		// 후보 팀이 있는 권역만 추려 셔플·순회한다. (풀에 상대 성별 후보 팀이 없는 권역은 헛순회라 건너뛴다)
		val populatedRegions: Set<Long> = pool.regionsWith(partnerGender)
		val nearbyPopulated: List<Long> = regionProximityPort.nearbyRegionIds(target.regionId)
			.filter { regionId: Long -> regionId in populatedRegions }
		val regionOrder: List<Long> = regionShuffler.shuffleNearest(nearbyPopulated)
		for (regionId: Long in regionOrder) {
			for (candidate: MatchableTeam in pool.freshCandidates(partnerGender, regionId)) {
				if (!getTeamMatchRecordDao.existsByPair(target.teamId, candidate.teamId)) return candidate
			}
		}
		return null
	}

	companion object {
		/** 후보로 인정하는 최근 로그인 기간(주). */
		private const val RECENT_LOGIN_WEEKS = 2L
	}
}
