package com.org.meeple.scheduler.match.command.application

import com.org.meeple.common.user.Gender
import com.org.meeple.scheduler.match.command.application.port.`in`.RunRecommendedTeamBatchUseCase
import com.org.meeple.scheduler.match.command.application.port.out.SaveRecommendedTeamPort
import com.org.meeple.scheduler.match.command.application.port.out.TimeGenerator
import com.org.meeple.scheduler.match.command.domain.RecommendedTeamBatchResult
import com.org.meeple.scheduler.match.query.dao.GetCandidateTeamDao
import com.org.meeple.scheduler.match.query.dao.GetRecommendableSoloUserDao
import com.org.meeple.scheduler.match.query.dto.RecommendableSoloUser
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * [RunRecommendedTeamBatchUseCase] 구현.
 * 팀 미소속 솔로 유저를 user_id 키셋 페이징으로 순회하며, 각자에게 반대 성별·같은 권역의 ACTIVE 팀 1개를 추천 적재(교체)한다.
 * 후보 팀이 없으면 건너뛴다. 전체를 한 트랜잭션으로 묶지 않으며, 한 사용자의 실패가 다른 사용자에게 전파되지 않게 사용자 단위로 격리한다.
 */
@Service
class RecommendedTeamBatchService(
	private val getRecommendableSoloUserDao: GetRecommendableSoloUserDao,
	private val getCandidateTeamDao: GetCandidateTeamDao,
	private val saveRecommendedTeamPort: SaveRecommendedTeamPort,
	private val timeGenerator: TimeGenerator,
) : RunRecommendedTeamBatchUseCase {

	private val log: Logger = LoggerFactory.getLogger(javaClass)

	override fun run(): RecommendedTeamBatchResult {
		val today: LocalDate = timeGenerator.today()

		var cursor: Long? = null
		var targets: Int = 0
		var recommended: Int = 0
		var skipped: Int = 0
		var failed: Int = 0

		while (true) {
			val page: List<RecommendableSoloUser> = getRecommendableSoloUserDao.findTargets(cursor, PAGE_SIZE)
			if (page.isEmpty()) break

			for (target: RecommendableSoloUser in page) {
				targets++
				try {
					// 팀은 동성 구성이므로, 요청자의 반대 성별 = 추천 팀의 성별.
					val teamGender: Gender = target.gender.opposite()
					val teamId: Long? = getCandidateTeamDao.findOneCandidateTeamId(teamGender, target.regionCode)
					if (teamId != null) {
						saveRecommendedTeamPort.replace(target.userId, teamId, today)
						recommended++
					} else {
						skipped++
					}
				} catch (e: Exception) {
					failed++
					log.warn("팀 추천 배치 처리 실패 userId={}", target.userId, e)
				}
			}

			cursor = page.last().userId
		}

		val result: RecommendedTeamBatchResult = RecommendedTeamBatchResult(targets = targets, recommended = recommended, skipped = skipped, failed = failed)
		log.info("팀 추천 배치 완료: {}", result)
		return result
	}

	companion object {
		/** 한 번에 조회·순회하는 대상 페이지 크기. */
		private const val PAGE_SIZE = 500
	}
}
