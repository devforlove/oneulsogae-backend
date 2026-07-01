package com.org.meeple.core.solomatch.query.service

import com.org.meeple.core.common.region.GetRegionProximityPort
import com.org.meeple.core.common.time.TimeGenerator
import com.org.meeple.core.matchuser.command.application.port.`in`.GetMatchUserUseCase
import com.org.meeple.core.matchuser.command.domain.MatchUser
import com.org.meeple.core.solomatch.query.dao.GetExtraIntroCandidateDao
import com.org.meeple.core.solomatch.query.dto.ExtraIntroCandidate
import com.org.meeple.core.solomatch.query.dto.ExtraIntroCandidates
import com.org.meeple.core.solomatch.query.dto.ExtraIntroScoringRow
import com.org.meeple.core.solomatch.query.service.port.`in`.GetExtraIntroCandidatesUseCase
import com.org.meeple.matching.MatchScoringProfile
import com.org.meeple.matching.MatchSelector
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.random.Random

/**
 * [GetExtraIntroCandidatesUseCase] 구현. 자격 후보를 종합 점수로 정렬해 상위 [DISPLAY_LIMIT]명의 표시 프로필과
 * 전체 자격 후보 수를 반환한다. 부수효과 없는 순수 조회다.
 */
@Service
@Transactional(readOnly = true)
class GetExtraIntroCandidatesService(
	private val getMatchUserUseCase: GetMatchUserUseCase,
	private val getExtraIntroCandidateDao: GetExtraIntroCandidateDao,
	private val getRegionProximityPort: GetRegionProximityPort,
	private val timeGenerator: TimeGenerator,
	private val random: Random = Random.Default,
) : GetExtraIntroCandidatesUseCase {

	override fun getCandidates(userId: Long): ExtraIntroCandidates {
		// 매칭 가능 상태가 아니면 후보도 없다. (읽기 모델 미적재)
		val requester: MatchUser = getMatchUserUseCase.findByUserId(userId)
			?: return ExtraIntroCandidates(totalCount = 0, candidates = emptyList())

		val now: LocalDateTime = timeGenerator.now()
		val loginAfter: LocalDateTime = now.minusWeeks(RECENT_LOGIN_WEEKS)
		val today: LocalDate = now.toLocalDate()

		val rows: List<ExtraIntroScoringRow> =
			getExtraIntroCandidateDao.findScoringRows(userId, requester.partnerGender(), loginAfter)
		if (rows.isEmpty()) return ExtraIntroCandidates(totalCount = 0, candidates = emptyList())

		val requesterProfile: MatchScoringProfile? = getExtraIntroCandidateDao.findRequesterProfile(userId, today)
		val nearby: List<Long> = getRegionProximityPort.nearbyRegionIds(requester.regionId)
		val rankByRegion: Map<Long, Int> = nearby.withIndex().associate { (index: Int, regionId: Long) -> regionId to index }

		val ordered: List<ExtraIntroScoringRow> = MatchSelector.orderByScore(
			targetProfile = requesterProfile,
			candidates = rows,
			profileOf = { row: ExtraIntroScoringRow -> row.profile },
			regionRankByRegionId = rankByRegion,
			regionCount = nearby.size,
			now = now,
			loginAfter = loginAfter,
			random = random,
		)

		val topUserIds: List<Long> = ordered.take(DISPLAY_LIMIT).map { row: ExtraIntroScoringRow -> row.userId }
		val profileByUserId: Map<Long, ExtraIntroCandidate> =
			getExtraIntroCandidateDao.findDisplayProfiles(topUserIds).associateBy { it.userId }
		// 점수 정렬 순서를 유지한다.
		val candidates: List<ExtraIntroCandidate> = topUserIds.mapNotNull { id: Long -> profileByUserId[id] }

		return ExtraIntroCandidates(totalCount = rows.size, candidates = candidates)
	}

	companion object {
		/** 응답으로 내려주는 후보 프로필 수. */
		private const val DISPLAY_LIMIT = 11
		/** 후보로 인정하는 최근 로그인 기간(주). */
		private const val RECENT_LOGIN_WEEKS = 2L
	}
}
