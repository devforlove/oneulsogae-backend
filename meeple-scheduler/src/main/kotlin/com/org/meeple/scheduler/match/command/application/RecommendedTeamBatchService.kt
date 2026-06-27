package com.org.meeple.scheduler.match.command.application

import com.org.meeple.common.user.Gender
import com.org.meeple.scheduler.match.command.application.port.`in`.RunRecommendedTeamBatchUseCase
import com.org.meeple.scheduler.match.command.application.port.out.RegionProximityPort
import com.org.meeple.scheduler.match.command.application.port.out.RegionShuffler
import com.org.meeple.scheduler.match.command.application.port.out.SaveRecommendedTeamPort
import com.org.meeple.scheduler.match.command.application.port.out.TimeGenerator
import com.org.meeple.scheduler.match.command.domain.RecommendedTeamBatchResult
import com.org.meeple.scheduler.match.command.domain.TeamPool
import com.org.meeple.scheduler.match.query.dao.GetCandidateTeamDao
import com.org.meeple.scheduler.match.query.dao.GetRecommendableSoloUserDao
import com.org.meeple.scheduler.match.query.dao.GetRecommendedTeamRecordDao
import com.org.meeple.scheduler.match.query.dao.GetUserMatchHistoryDao
import com.org.meeple.scheduler.match.query.dto.PreviouslyMatchedTeams
import com.org.meeple.scheduler.match.query.dto.RecommendableSoloUser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * [RunRecommendedTeamBatchUseCase] 구현. 매일 도는 근접 기반 팀 추천 배치. (솔로 매칭 배치와 동일 골격)
 *
 * 시작 시 regionProximityPort.refresh()로 근접 스냅샷을 최신화하고, "오늘 이미 추천된 유저"를 인메모리 집합으로 제외한다.
 * 팀 미소속 솔로 유저를 전체 1회 적재해 순회하며, 각자에게 [RegionProximityPort.nearbyRegionIds]로 가까운 권역부터
 * 반대 성별 ACTIVE 팀을 찾아([RegionShuffler]로 근접 상위 N권역을 섞음) 그 권역의 팀 1개를 무작위로 골라 추천 적재(교체)한다.
 * 후보가 없으면 건너뛴다. 각 유저가 과거 소속 팀으로 MATCHED됐던 상대 팀은 그 유저에게 다시 추천하지 않는다(유저별 재매칭 제외).
 * 한 사용자의 실패가 다른 사용자에 전파되지 않도록 격리한다.
 */
@Service
class RecommendedTeamBatchService(
    private val getRecommendableSoloUserDao: GetRecommendableSoloUserDao,
    private val getRecommendedTeamRecordDao: GetRecommendedTeamRecordDao,
    private val getCandidateTeamDao: GetCandidateTeamDao,
    private val getUserMatchHistoryDao: GetUserMatchHistoryDao,
    private val saveRecommendedTeamPort: SaveRecommendedTeamPort,
    private val regionProximityPort: RegionProximityPort,
    private val regionShuffler: RegionShuffler,
    private val timeGenerator: TimeGenerator,
    private val random: Random = Random.Default,
) : RunRecommendedTeamBatchUseCase {

    private val log: Logger = LoggerFactory.getLogger(javaClass)

    override fun run(): RecommendedTeamBatchResult {
        val now: LocalDateTime = timeGenerator.now()
        val loginAfter: LocalDateTime = now.minusWeeks(RECENT_LOGIN_WEEKS)
        val today: LocalDate = now.toLocalDate()

        // 근접 스냅샷을 최신화한다. (가까운 권역 순서 계산의 기준)
        regionProximityPort.refresh()

        // 하루 1회: 오늘 이미 추천받은 유저는 제외한다. (재실행 멱등)
        val excluded: Set<Long> = getRecommendedTeamRecordDao.findUserIdsRecommendedOn(today)
        val targets: List<RecommendableSoloUser> = getRecommendableSoloUserDao.findRecommendableSoloUsers(loginAfter)
            .filterNot { user: RecommendableSoloUser -> user.userId in excluded }
        val pool: TeamPool = TeamPool.of(getCandidateTeamDao.findCandidateTeams())
        // 대상 유저들이 과거 MATCHED됐던 상대 팀을 한 번에 적재한다. (유저별 재매칭 제외)
        val previouslyMatched: PreviouslyMatchedTeams =
            getUserMatchHistoryDao.findPreviouslyMatchedTeamIdsByUser(targets.map { user: RecommendableSoloUser -> user.userId }.toSet())

        var recommended = 0
        var skipped = 0
        var failed = 0
        for (target: RecommendableSoloUser in targets) {
            try {
                val teamId: Long? = findNearestRandomTeam(target, pool, previouslyMatched.opponentTeamIdsOf(target.userId))
                if (teamId == null) {
                    skipped++
                    continue
                }
                saveRecommendedTeamPort.replace(target.userId, teamId, today)
                recommended++
            } catch (e: Exception) {
                failed++
                log.warn("팀 추천 배치 처리 실패 userId={}", target.userId, e)
            }
        }

        val result: RecommendedTeamBatchResult = RecommendedTeamBatchResult(targets = targets.size, recommended = recommended, skipped = skipped, failed = failed)
        log.info("팀 추천 배치 완료: {}", result)
        return result
    }

    /** [target] 권역에서 가까운 순 상위 N권역을 무작위 순서로 뒤져, [excludedTeamIds]를 뺀 후보 팀이 있는 첫 권역에서 무작위 팀 1개를 고른다. (없으면 null) */
    private fun findNearestRandomTeam(target: RecommendableSoloUser, pool: TeamPool, excludedTeamIds: Set<Long>): Long? {
        // 팀은 동성 구성이므로, 요청자의 반대 성별 = 추천 팀의 성별.
        val teamGender: Gender = target.gender.opposite()
        val regionOrder: List<Long> = regionShuffler.shuffleNearest(regionProximityPort.nearbyRegionIds(target.regionId))
        for (regionId: Long in regionOrder) {
            val teamIds: List<Long> = pool.teamIdsOf(teamGender, regionId).filterNot { teamId: Long -> teamId in excludedTeamIds }
            if (teamIds.isNotEmpty()) return teamIds.random(random)
        }
        return null
    }

    companion object {
        /** 추천 대상으로 인정하는 최근 로그인 기간(주). */
        private const val RECENT_LOGIN_WEEKS = 2L
    }
}
